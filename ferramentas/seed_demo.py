# -*- coding: utf-8 -*-
"""Recheia o banco do Connect ONG com uma massa de demonstração realista.

Para que serve
--------------
Até aqui o banco tinha só os dados usados para TESTAR funcionalidades: 20 ONGs
com descrição genérica, sem endereço, sem nota e sem histórico. Numa
apresentação isso denuncia que o produto é novo. Este script produz o oposto:
milhares de ONGs espalhadas pelo Brasil (com coordenada real de município),
doadores com nome e telefone plausíveis, conversas completas, prestações de
contas, avaliações, campanhas e doações PIX distribuídas ao longo de ~18 meses.

Como rodar
----------
    python ferramentas/seed_demo.py --ongs 2000 --doadores 1200
    python ferramentas/seed_demo.py --dry-run          # gera e desfaz (rollback)
    python ferramentas/seed_demo.py --limpar           # remove o que ESTE script criou

A senha do banco vem, nesta ordem: --senha, variável DB_PASSWORD, ou o
application-local.properties do backend. Para a máquina da feira (banco local)
use --host/--usuario/--banco.

Detalhes que importam
---------------------
* O banco da escola é MySQL 5.6 com colunas **latin1**. Todo texto é validado
  antes de gravar; emoji e travessão longo abortam a execução.
* Os IDs são atribuídos explicitamente pelo script (MAX(id)+1 em diante). Isso
  permite montar as relações (necessidade -> interesse -> mensagens ->
  prestação -> avaliação) sem depender de lastrowid e deixa o registro do que
  foi criado no manifesto `ferramentas/dados_demo/manifesto_seed.json`, usado
  pelo --limpar.
* NADA existente é apagado. As ONGs e contas que já estavam no banco são
  ENRIQUECIDAS (ganham endereço, coordenada, descrição real e nota), inclusive
  as contas de demonstração usadas na feira, que mantêm id, e-mail e senha.
"""
import argparse
import json
import os
import pathlib
import random
import re
import sys
import unicodedata
from datetime import datetime, timedelta

import pymysql

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from dados_demo import conteudo as C  # noqa: E402
from dados_demo import demo_feira as D  # noqa: E402

RAIZ = pathlib.Path(__file__).resolve().parent.parent
BACKEND = RAIZ / "API - Chinelatto - att2" / "API - Chinelatto" / "API - Chinelatto"
GEO = BACKEND / "src" / "main" / "resources" / "geo"
DADOS = pathlib.Path(__file__).parent / "dados_demo"
MANIFESTO = DADOS / "manifesto_seed.json"

# Hash BCrypt de "demo123" (o mesmo usado pelas contas de demonstração atuais).
# Contas geradas por este script usam essa senha; é massa de demonstração.
SENHA_HASH = None  # descoberto no banco (conta demo.joao) ou usa o padrão abaixo
SENHA_HASH_PADRAO = "$2a$10$d1C6xJ1FUW0mIkGvBQOQF.hFJ7cAcOEQZKQDtCHnH4KMDDF0mdKvW"

HOJE = datetime(2026, 8, 11, 12, 0, 0)

# Peso populacional aproximado por UF (IBGE) - distribui as ONGs pelo país de
# um jeito que parece natural: SP tem muito mais que RR.
PESO_UF = {
    "SP": 210, "MG": 100, "RJ": 80, "BA": 70, "PR": 56, "RS": 53, "PE": 46,
    "CE": 44, "PA": 42, "SC": 38, "GO": 35, "MA": 34, "AM": 21, "ES": 20,
    "PB": 20, "MT": 19, "RN": 17, "AL": 16, "PI": 16, "DF": 15, "MS": 14,
    "SE": 11, "RO": 8, "TO": 8, "AC": 5, "AP": 4, "RR": 3,
}


# ---------------------------------------------------------------------------
# Infra
# ---------------------------------------------------------------------------
def senha_do_backend():
    arq = BACKEND / "src" / "main" / "resources" / "application-local.properties"
    if arq.exists():
        m = re.search(r"^DB_PASSWORD=(.*)$", arq.read_text(encoding="utf-8", errors="ignore"), re.M)
        if m:
            return m.group(1).strip()
    return None


def conectar(a):
    senha = a.senha or os.environ.get("DB_PASSWORD") or senha_do_backend()
    if not senha:
        sys.exit("Sem senha do banco: use --senha, a variavel DB_PASSWORD ou o application-local.properties")
    return pymysql.connect(host=a.host, port=a.porta, user=a.usuario, password=senha,
                           database=a.banco, charset="latin1", connect_timeout=30,
                           autocommit=False)


def sem_acento(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def slug(s):
    s = sem_acento(s).lower()
    return re.sub(r"[^a-z0-9]+", "", s)[:28] or "ong"


def validar_texto(valor, onde):
    """Aborta se o texto não couber no latin1 do banco (emoji, travessão longo...)."""
    if isinstance(valor, str) and not C.checar_latin1(valor):
        ruins = {c for c in valor if not C.checar_latin1(c)}
        sys.exit(f"Texto invalido para o banco latin1 em {onde}: {ruins} -> {valor[:60]!r}")
    return valor


class Ids:
    """Distribui IDs explícitos a partir do MAX(id) atual de cada tabela."""

    def __init__(self, cur):
        self.cur = cur
        self.prox = {}

    def novo(self, tabela, quantos=None):
        """Sem `quantos`, devolve UM id (int). Com `quantos`, devolve uma LISTA.

        A distinção é pelo parâmetro, não pelo valor: pedir 1 id de uma lista
        (uma ONG com uma única campanha, por exemplo) tem que continuar
        devolvendo lista, senão o índice [0] estoura.
        """
        if tabela not in self.prox:
            self.cur.execute(f"SELECT COALESCE(MAX(id), 0) FROM `{tabela}`")
            self.prox[tabela] = self.cur.fetchone()[0] + 1
        ini = self.prox[tabela]
        self.prox[tabela] += (1 if quantos is None else quantos)
        return ini if quantos is None else list(range(ini, ini + quantos))


# ---------------------------------------------------------------------------
# Geografia
# ---------------------------------------------------------------------------
def carregar_geo():
    municipios = json.loads((GEO / "municipios.json").read_text(encoding="utf-8-sig"))
    estados = json.loads((GEO / "estados.json").read_text(encoding="utf-8-sig"))
    uf_por_codigo = {e["codigo_uf"]: e["uf"] for e in estados}
    por_uf = {}
    for m in municipios:
        uf = uf_por_codigo.get(m["codigo_uf"])
        if not uf:
            continue
        m["uf"] = uf
        por_uf.setdefault(uf, []).append(m)
    return por_uf


def sortear_cidades(por_uf, quantas, rnd):
    """Sorteia cidades pelo peso populacional da UF; capitais concentram mais."""
    ufs = list(PESO_UF)
    pesos = [PESO_UF[u] for u in ufs]
    escolhidas = []
    for _ in range(quantas):
        uf = rnd.choices(ufs, weights=pesos)[0]
        lista = por_uf.get(uf) or []
        if not lista:
            continue
        # capital pesa 30x: o mapa fica denso nas capitais e salpicado no interior
        pesos_cid = [30 if m.get("capital") else 1 for m in lista]
        escolhidas.append(rnd.choices(lista, weights=pesos_cid)[0])
    return escolhidas


# ---------------------------------------------------------------------------
# Geradores de texto
# ---------------------------------------------------------------------------
def nome_ong(causa, cidade, rnd, usados):
    for _ in range(60):
        nucleo = rnd.choice(causa["nucleos"])
        estilo = rnd.random()
        if estilo < 0.45:
            nome = f"{rnd.choice(C.PREFIXOS)} {nucleo}"
        elif estilo < 0.65:
            nome = f"{rnd.choice(C.PREFIXOS)} {nucleo} {rnd.choice(C.COMPLEMENTOS)}"
        elif estilo < 0.85:
            nome = f"{nucleo} {cidade['nome']}"
        else:
            nome = f"{rnd.choice(C.PREFIXOS)} {nucleo} de {cidade['nome']}"
        nome = nome[:100]
        if nome.lower() not in usados:
            usados.add(nome.lower())
            return nome
    n = f"{rnd.choice(C.PREFIXOS)} {rnd.choice(causa['nucleos'])} {rnd.randint(2, 99)}"[:100]
    usados.add(n.lower())
    return n


def descricao_ong(causa, cidade, bairro, rnd):
    prog = rnd.sample(causa["programas"], 2)
    item = rnd.choice([n[0].lower() for n in causa["necessidades"]])
    texto = rnd.choice(C.MOLDES_DESCRICAO).format(
        ano=rnd.randint(1996, 2023),
        atendidos=rnd.choice([28, 35, 40, 52, 60, 75, 80, 96, 110, 120, 140, 160, 180, 210, 240]),
        publico=rnd.choice(causa["publico"]),
        cidade=cidade["nome"],
        bairro=bairro,
        prog1=prog[0].replace("{n}", str(rnd.randint(8, 40))),
        prog2=prog[1].replace("{n}", str(rnd.randint(8, 40))),
        vol=rnd.randint(6, 45),
        prof=rnd.randint(2, 14),
        item=item,
    )
    return texto[:1000]


def numeros(texto, rnd):
    """Preenche os marcadores numéricos dos textos de necessidade/prestação."""
    return (texto.replace("{n2}", str(rnd.choice([12, 18, 25, 30, 45, 60, 80, 120])))
                 .replace("{n}", str(rnd.choice([15, 20, 25, 30, 40, 50, 60, 80, 100, 120, 150, 200])))
                 .replace("{meses}", str(rnd.choice([2, 3, 4, 6, 8, 12]))))


def telefone(ddd, rnd, fixo=False):
    if fixo:
        return f"({ddd:02d}) {rnd.randint(2, 3)}{rnd.randint(100, 999)}-{rnd.randint(1000, 9999)}"
    return f"({ddd:02d}) 9{rnd.randint(1000, 9999)}-{rnd.randint(1000, 9999)}"


def cnpj(rnd):
    return (f"{rnd.randint(10, 99)}.{rnd.randint(100, 999)}.{rnd.randint(100, 999)}"
            f"/0001-{rnd.randint(10, 99)}")


def endereco_de(cidade, bairro, rnd):
    return f"{rnd.choice(C.VIAS)}, {rnd.randint(20, 2400)} - {bairro}, {cidade['nome']} - {cidade['uf']}"[:255]


def coordenada(cidade, rnd):
    """Coordenada real do município com dispersão de ~2 km (pinos não empilham)."""
    return (round(cidade["latitude"] + rnd.uniform(-0.02, 0.02), 6),
            round(cidade["longitude"] + rnd.uniform(-0.02, 0.02), 6))


def data_entre(ini, fim, rnd):
    delta = int((fim - ini).total_seconds())
    return ini + timedelta(seconds=rnd.randint(0, max(delta, 1)))


def nome_pessoa(rnd):
    primeiro = rnd.choice(C.NOMES_M if rnd.random() < 0.48 else C.NOMES_F)
    return f"{primeiro} {rnd.choice(C.SOBRENOMES)} {rnd.choice(C.SOBRENOMES)}" \
        if rnd.random() < 0.35 else f"{primeiro} {rnd.choice(C.SOBRENOMES)}"


# ---------------------------------------------------------------------------
# Inserção
# ---------------------------------------------------------------------------
def inserir(cur, tabela, colunas, linhas, lote=400):
    if not linhas:
        return
    for l in linhas:
        for v in l:
            validar_texto(v, tabela)
    sql = (f"INSERT INTO `{tabela}` (`{'`,`'.join(colunas)}`) "
           f"VALUES ({','.join(['%s'] * len(colunas))})")
    for i in range(0, len(linhas), lote):
        cur.executemany(sql, linhas[i:i + lote])


def main():
    p = argparse.ArgumentParser(description="Recheia o banco do Connect ONG para a apresentacao")
    p.add_argument("--host", default="143.106.241.3")
    p.add_argument("--porta", type=int, default=3306)
    p.add_argument("--usuario", default="cl203161")
    p.add_argument("--banco", default="cl203161")
    p.add_argument("--senha", default=None)
    p.add_argument("--ongs", type=int, default=2000, help="total de ONGs desejado no banco")
    p.add_argument("--doadores", type=int, default=1200, help="total de doadores desejado")
    p.add_argument("--abertas", type=int, default=900,
                   help="quantas necessidades ficam ABERTA (peso do feed do doador)")
    p.add_argument("--semente", type=int, default=20260811)
    p.add_argument("--dry-run", action="store_true", help="gera tudo e desfaz no final")
    p.add_argument("--limpar", action="store_true", help="remove o que este script criou")
    p.add_argument("--somente-arrumar", action="store_true",
                   help="so arruma os cadastros e necessidades de teste (nao gera massa nova)")
    a = p.parse_args()

    rnd = random.Random(a.semente)
    con = conectar(a)
    cur = con.cursor()

    if a.limpar:
        limpar(con, cur)
        return

    if a.somente_arrumar:
        arrumar_cadastros_de_teste(cur, rnd)
        arrumar_necessidades_legadas(cur)
        con.commit()
        resumo(cur)
        return

    ids = Ids(cur)
    manifesto = {"criado_em": HOJE.isoformat(), "faixas": {}}

    def checkpoint():
        """Grava o progresso - exceto no dry-run, que precisa desfazer tudo."""
        if not a.dry_run:
            con.commit()

    def registrar(tabela, lista_ids):
        """Acumula a faixa de ids criada por tabela.

        Tem que ACUMULAR, não substituir: as contas da feira registram um
        interesse por vez, e sobrescrever deixaria o manifesto apontando só
        para o último id - o --limpar então tentaria apagar uma necessidade
        cujo interesse continuava vivo e batia na chave estrangeira.
        """
        if not lista_ids:
            return
        ini, fim = min(lista_ids), max(lista_ids)
        atual = manifesto["faixas"].get(tabela)
        manifesto["faixas"][tabela] = ([ini, fim] if atual is None
                                       else [min(atual[0], ini), max(atual[1], fim)])

    global SENHA_HASH
    cur.execute("SELECT senha FROM usuario WHERE email = 'demo.joao@connectong.com'")
    r = cur.fetchone()
    SENHA_HASH = r[0] if r else SENHA_HASH_PADRAO

    por_uf = carregar_geo()
    print(f"Geografia: {sum(len(v) for v in por_uf.values())} municipios em {len(por_uf)} UFs")

    # ---------------- ONGs -------------------------------------------------
    cur.execute("SELECT COUNT(*) FROM ong WHERE data_exclusao IS NULL")
    ongs_hoje = cur.fetchone()[0]
    novas = max(a.ongs - ongs_hoje, 0)
    cidades = sortear_cidades(por_uf, novas, rnd)
    usados = set()
    cur.execute("SELECT LOWER(nome) FROM ong")
    usados.update(x[0] for x in cur.fetchall())
    cur.execute("SELECT LOWER(email) FROM usuario UNION SELECT LOWER(email) FROM ong")
    emails_usados = {x[0] for x in cur.fetchall()}

    ong_ids = ids.novo("ong", len(cidades))
    linhas_ong, ongs = [], []
    for i, cidade in enumerate(cidades):
        causa = rnd.choice(C.CAUSAS)
        bairro = rnd.choice(C.BAIRROS)
        nome = nome_ong(causa, cidade, rnd, usados)
        base = slug(nome)
        email = f"contato@{base}.org.br"
        n = 2
        while email.lower() in emails_usados:
            email = f"contato@{base}{n}.org.br"
            n += 1
        emails_usados.add(email.lower())
        lat, lon = coordenada(cidade, rnd)
        # Perfil de maturidade: instituições antigas e ativas concentram nota e selo
        maturidade = rnd.random()
        verificada = 1 if maturidade > 0.42 else 0
        if maturidade > 0.30:
            total_av = rnd.randint(1, 40)
            nota = round(rnd.triangular(3.4, 5.0, 4.7), 1)
        else:
            total_av, nota = 0, 0.0
        ongs.append({
            "id": ong_ids[i], "nome": nome, "causa": causa, "cidade": cidade,
            "bairro": bairro, "email": email, "maturidade": maturidade,
            "endereco": endereco_de(cidade, bairro, rnd),
        })
        linhas_ong.append((
            ong_ids[i], f"{cidade['nome']} - {cidade['uf']}"[:50],
            descricao_ong(causa, cidade, bairro, rnd), email, nome,
            telefone(cidade["ddd"], rnd, fixo=rnd.random() < 0.65),
            cnpj(rnd) if rnd.random() < 0.8 else None,
            verificada, nota, total_av, None, None,
            ongs[-1]["endereco"], None, None, lat, lon,
        ))
    inserir(cur, "ong",
            ["id", "cidade", "descricao", "email", "nome", "telefone", "cnpj", "verificada",
             "nota_media", "total_avaliacoes", "data_exclusao", "capa_base64", "endereco",
             "top1_desde", "ultimo_reinado_dias", "latitude", "longitude"], linhas_ong)
    registrar("ong", ong_ids)
    print(f"ONGs novas: {len(linhas_ong)}")

    # ONGs que já existiam: completa o que estiver faltando (sem apagar nada)
    arrumar_cadastros_de_teste(cur, rnd)
    arrumar_necessidades_legadas(cur)
    enriquecer_existentes(cur, por_uf, rnd)

    # ---------------- Doadores --------------------------------------------
    cur.execute("SELECT COUNT(*) FROM usuario WHERE tipo = 'DOADOR' AND data_exclusao IS NULL")
    doadores_hoje = cur.fetchone()[0]
    qtd_doadores = max(a.doadores - doadores_hoje, 0)
    cidades_d = sortear_cidades(por_uf, qtd_doadores, rnd)
    doador_ids = ids.novo("usuario", qtd_doadores)
    linhas_u, doadores = [], []
    for i, cidade in enumerate(cidades_d):
        nome = nome_pessoa(rnd)
        base = slug(nome)
        email = f"{base}{rnd.randint(1, 9999)}@email.com"
        while email.lower() in emails_usados:
            email = f"{base}{rnd.randint(1, 99999)}@email.com"
        emails_usados.add(email.lower())
        criado = data_entre(datetime(2025, 2, 1), HOJE - timedelta(days=3), rnd)
        visto = data_entre(criado, HOJE, rnd) if rnd.random() < 0.8 else None
        doadores.append({"id": doador_ids[i], "nome": nome, "cidade": cidade, "criado": criado})
        linhas_u.append((
            doador_ids[i], email, nome, SENHA_HASH, "DOADOR", 1, criado, None,
            rnd.choice(C.BIOS_DOADOR) or None, cidade["nome"], cidade["uf"], None,
            telefone(cidade["ddd"], rnd), None, visto, None, None, None,
        ))
    inserir(cur, "usuario",
            ["id", "email", "nome", "senha", "tipo", "ativo", "criado_em", "ong_id", "bio",
             "cidade", "estado", "foto_url", "telefone", "foto_base64", "ultimo_visto",
             "data_exclusao", "nota_media_doador", "total_avaliacoes_doador"], linhas_u)
    registrar("usuario", doador_ids)
    print(f"Doadores novos: {len(linhas_u)}")

    # doadores que já existiam entram no sorteio das interações
    cur.execute("SELECT id, nome, cidade, estado FROM usuario "
                "WHERE tipo = 'DOADOR' AND data_exclusao IS NULL AND id < %s", (doador_ids[0],))
    for did, dnome, dcid, duf in cur.fetchall():
        doadores.append({"id": did, "nome": dnome,
                         "cidade": {"nome": dcid or "Limeira", "uf": duf or "SP", "ddd": 19},
                         "criado": datetime(2025, 3, 1)})

    # todas as ONGs (novas + antigas) participam das interações. A causa das
    # antigas vem do NOME, não de sorteio: senão a Lar Viva, que é um lar de
    # idosos, apareceria pedindo ração de cachorro no dia da feira.
    cur.execute("SELECT id, nome, cidade, endereco, email FROM ong WHERE data_exclusao IS NULL "
                "AND id < %s", (ong_ids[0],))
    for oid, onome, ocid, oend, oemail in cur.fetchall():
        ongs.append({"id": oid, "nome": onome, "causa": causa_por_nome(onome, rnd),
                     "cidade": {"nome": (ocid or "Limeira").split(" - ")[0], "uf": "SP", "ddd": 19},
                     "bairro": "Centro", "email": oemail or "", "maturidade": 0.9,
                     "endereco": oend or "Centro",
                     "demo": (oemail or "").lower() in D.ONGS_DEMO})

    checkpoint()
    print("ONGs e doadores gravados. Montando o historico...")

    # ---------------- Necessidades ----------------------------------------
    linhas_nec, necessidades = [], []
    for o in ongs:
        if o.get("demo"):
            continue          # as ONGs da feira têm necessidades escritas à mão
        for titulo, categoria, desc in rnd.sample(o["causa"]["necessidades"],
                                                  min(rnd.randint(2, 5), len(o["causa"]["necessidades"]))):
            necessidades.append({"ong": o, "titulo": titulo, "categoria": categoria,
                                 "descricao": numeros(desc, rnd),
                                 "criada": data_entre(datetime(2025, 2, 1), HOJE - timedelta(days=2), rnd)})
    # só uma parte fica ABERTA: é o que pesa no feed do doador
    rnd.shuffle(necessidades)
    nec_ids = ids.novo("necessidade", len(necessidades))
    for i, n in enumerate(necessidades):
        n["id"] = nec_ids[i]
        n["status"] = "ABERTA" if i < a.abertas else "ATENDIDA"
        if n["status"] == "ABERTA":
            n["criada"] = data_entre(HOJE - timedelta(days=75), HOJE - timedelta(days=1), rnd)
        linhas_nec.append((n["id"], n["categoria"], n["criada"], n["descricao"][:255],
                           n["status"], n["titulo"][:150],
                           1 if rnd.random() < 0.22 else 0, n["ong"]["id"]))
    inserir(cur, "necessidade",
            ["id", "categoria", "data_criacao", "descricao", "status", "titulo", "urgente", "ong_id"],
            linhas_nec)
    registrar("necessidade", nec_ids)
    print(f"Necessidades: {len(linhas_nec)} ({a.abertas} abertas)")

    # ---------------- Campanhas -------------------------------------------
    linhas_camp, campanhas = [], []
    for o in ongs:
        if o.get("demo"):
            continue          # idem: campanhas da feira são escritas à mão
        if rnd.random() > (0.65 if o["maturidade"] > 0.4 else 0.25):
            continue
        for titulo, categoria, desc in rnd.sample(o["causa"]["campanhas"], rnd.randint(1, 2)):
            inicio = data_entre(datetime(2025, 3, 1), HOJE - timedelta(days=20), rnd).date()
            dur = rnd.choice([30, 45, 60, 90, 120])
            fim = inicio + timedelta(days=dur)
            # metas de campanha de bairro: quantia que uma comunidade arrecada
            # de verdade em 1 a 4 meses, não orçamento de multinacional
            meta = rnd.choice([800, 1000, 1200, 1500, 1800, 2000, 2500, 3000, 4000, 5000, 6000])
            encerrada = fim < HOJE.date()
            if encerrada:
                arrecadado = round(meta * rnd.triangular(0.75, 1.35, 1.02), 2)
            else:
                arrecadado = round(meta * rnd.triangular(0.05, 0.85, 0.35), 2)
            campanhas.append({"ong": o, "titulo": titulo, "meta": meta,
                              "arrecadado": arrecadado, "inicio": inicio, "fim": fim,
                              "encerrada": encerrada})
    camp_ids = ids.novo("campanha", len(campanhas))
    for i, c in enumerate(campanhas):
        c["id"] = camp_ids[i]
        desc = next(d for t, _, d in c["ong"]["causa"]["campanhas"] if t == c["titulo"])
        linhas_camp.append((c["id"], numeros(desc, rnd)[:255], c["meta"], c["titulo"][:150],
                            c["ong"]["id"], c["arrecadado"],
                            next(cat for t, cat, _ in c["ong"]["causa"]["campanhas"] if t == c["titulo"]),
                            c["inicio"], c["fim"], 1 if c["encerrada"] else 0,
                            1 if rnd.random() < 0.12 else 0))
    inserir(cur, "campanha",
            ["id", "descricao", "meta_valor", "titulo", "ong_id", "valor_arrecadado",
             "categoria", "data_inicio", "data_fim", "encerrada", "destaque"], linhas_camp)
    registrar("campanha", camp_ids)
    print(f"Campanhas: {len(linhas_camp)}")

    checkpoint()

    # ---------------- Interesses (o match) --------------------------------
    interesses = []
    for n in necessidades:
        if n["status"] == "ABERTA":
            qtd = rnd.choices([0, 1, 2, 3], weights=[45, 30, 17, 8])[0]
        else:
            qtd = rnd.choices([1, 2, 3], weights=[60, 30, 10])[0]
        for _ in range(qtd):
            d = rnd.choice(doadores)
            criada = data_entre(n["criada"], min(n["criada"] + timedelta(days=25), HOJE), rnd)
            # A proporção aqui decide dois números que o portal mostra LADO A
            # LADO: "Conexões (matches)" conta só os interesses ACEITO, e
            # "Prestações de contas" vem dos CONCLUÍDOS. Com poucos ACEITO o
            # portal exibia 1.416 conexões e 5.711 prestações - ou seja, mais
            # prestações do que entregas, o que qualquer avaliador estranharia.
            if n["status"] == "ABERTA":
                status = rnd.choices(["PENDENTE", "ACEITO", "RECUSADO"], weights=[50, 43, 7])[0]
            else:
                status = rnd.choices(["CONCLUIDO", "ACEITO", "RECUSADO"], weights=[45, 47, 8])[0]
            data_status = data_entre(criada, min(criada + timedelta(days=6), HOJE), rnd) \
                if status != "PENDENTE" else None
            conclusao = data_entre(data_status, min(data_status + timedelta(days=20), HOJE), rnd) \
                if status == "CONCLUIDO" else None
            interesses.append({"nec": n, "doador": d, "status": status, "criada": criada,
                               "data_status": data_status, "conclusao": conclusao})
    int_ids = ids.novo("interesse", len(interesses))
    linhas_int = []
    for i, it in enumerate(interesses):
        it["id"] = int_ids[i]
        linhas_int.append((it["id"], it["criada"], it["status"], it["doador"]["id"],
                           it["nec"]["id"], it["conclusao"], it["data_status"]))
    inserir(cur, "interesse",
            ["id", "data_criacao", "status", "doador_id", "necessidade_id",
             "data_conclusao", "data_status"], linhas_int)
    registrar("interesse", int_ids)
    print(f"Interesses (matches): {len(linhas_int)}")
    checkpoint()

    # ---------------- Conversas -------------------------------------------
    linhas_msg = []
    com_conversa = [i for i in interesses if i["status"] in ("CONCLUIDO", "ACEITO")]
    rnd.shuffle(com_conversa)
    com_conversa = com_conversa[:2500]  # conversas custam linhas; 2500 já enche a tela
    ids_msg = ids.novo("mensagem", sum(8 for _ in com_conversa))
    pos = 0
    for it in com_conversa:
        concluido = it["status"] == "CONCLUIDO"
        roteiro = rnd.choice(C.ROTEIROS_CHAT if concluido else C.ROTEIROS_CHAT_ABERTO)
        quando = it["data_status"] or it["criada"]
        for remetente, texto in roteiro:
            quando = quando + timedelta(minutes=rnd.randint(3, 400))
            if quando > HOJE:
                break
            corpo = texto.format(item=it["nec"]["titulo"].lower(),
                                 endereco=it["nec"]["ong"]["endereco"])
            linhas_msg.append((ids_msg[pos], corpo[:1000], quando, remetente, it["id"],
                               quando + timedelta(minutes=rnd.randint(1, 240)), None, None))
            pos += 1
    inserir(cur, "mensagem",
            ["id", "conteudo", "data_envio", "remetente", "interesse_id", "data_leitura",
             "anexo_base64", "anexo_tipo"], linhas_msg)
    registrar("mensagem", [l[0] for l in linhas_msg])
    print(f"Mensagens de chat: {len(linhas_msg)} em {len(com_conversa)} conversas")
    checkpoint()

    # ---------------- Prestações de contas --------------------------------
    concluidos = [i for i in interesses if i["status"] == "CONCLUIDO"]
    # a maioria presta contas (é o que sustenta o score de transparência);
    # alguns ficam pendentes de propósito, para o painel ter o que cobrar
    presta = [i for i in concluidos if rnd.random() < 0.78]
    prest_ids = ids.novo("prestacao", len(presta))
    linhas_prest = []
    for i, it in enumerate(presta):
        titulo, desc = rnd.choice(it["nec"]["ong"]["causa"]["prestacoes"])
        quando = data_entre(it["conclusao"], min(it["conclusao"] + timedelta(days=9), HOJE), rnd)
        linhas_prest.append((prest_ids[i], quando, numeros(desc, rnd)[:1000], None,
                             titulo[:150], it["id"],
                             round(rnd.uniform(80, 2500), 2) if rnd.random() < 0.5 else None))
    inserir(cur, "prestacao",
            ["id", "data_criacao", "descricao", "foto_url", "titulo", "interesse_id",
             "valor_utilizado"], linhas_prest)
    registrar("prestacao", prest_ids)
    print(f"Prestacoes de contas: {len(linhas_prest)}")

    # ---------------- Avaliações ------------------------------------------
    linhas_av, avaliadores = [], {}
    candidatos = [i for i in concluidos if rnd.random() < 0.62]
    av_ids = ids.novo("avaliacao", len(candidatos))
    for i, it in enumerate(candidatos):
        nota = rnd.choices([5, 4, 3], weights=[74, 21, 5])[0]
        quando = data_entre(it["conclusao"], min(it["conclusao"] + timedelta(days=14), HOJE), rnd)
        linhas_av.append((av_ids[i], rnd.choice(it["nec"]["ong"]["causa"]["avaliacoes"])[:500],
                          quando, it["doador"]["id"], it["doador"]["nome"], nota,
                          it["nec"]["ong"]["id"]))
        avaliadores.setdefault(it["nec"]["ong"]["id"], []).append(nota)
    inserir(cur, "avaliacao",
            ["id", "comentario", "data_criacao", "doador_id", "doador_nome", "nota", "ong_id"],
            linhas_av)
    registrar("avaliacao", av_ids)
    print(f"Avaliacoes de ONG: {len(linhas_av)}")

    # avaliação da ONG sobre o doador (reputação estilo Uber, dos dois lados)
    alvo_doador = [i for i in concluidos if rnd.random() < 0.35]
    avd_ids = ids.novo("avaliacao_doador", len(alvo_doador))
    linhas_avd, notas_doador = [], {}
    vistos = set()
    for i, it in enumerate(alvo_doador):
        chave = (it["doador"]["id"], it["nec"]["ong"]["id"])
        if chave in vistos:
            continue
        vistos.add(chave)
        nota = rnd.choices([5, 4, 3], weights=[80, 17, 3])[0]
        quando = data_entre(it["conclusao"], min(it["conclusao"] + timedelta(days=10), HOJE), rnd)
        linhas_avd.append((avd_ids[i], it["doador"]["id"], it["nec"]["ong"]["id"], nota,
                           rnd.choice(["Doador pontual, entregou tudo conforme combinado.",
                                       "Muito atencioso, avisou antes de vir. Recomendamos.",
                                       "Doação em ótimo estado, bem embalada.",
                                       "Cumpriu o horário e ainda ajudou a descarregar.",
                                       "Excelente contato, respondeu rápido pelo chat."])[:500],
                           quando, quando))
        notas_doador.setdefault(it["doador"]["id"], []).append(nota)
    inserir(cur, "avaliacao_doador",
            ["id", "doador_id", "ong_id", "nota", "comentario", "criado_em", "atualizado_em"],
            linhas_avd)
    registrar("avaliacao_doador", [l[0] for l in linhas_avd])
    print(f"Avaliacoes de doador: {len(linhas_avd)}")
    checkpoint()

    # ---------------- Doações financeiras (PIX) ---------------------------
    linhas_pix = []
    pix_alvo = []
    # As doações vêm PRIMEIRO e a arrecadação da campanha é a soma delas.
    # (Ao contrário: sortear o total e fatiar deixava uma doação gigante de
    # sobra, tipo um PIX de R$ 18.000 numa campanha de bairro.)
    TICKETS = [10, 20, 20, 25, 30, 30, 50, 50, 50, 75, 100, 100, 150, 200, 250, 300, 500]
    for c in campanhas:
        alvo, soma, doacoes = c["arrecadado"], 0.0, 0
        while soma < alvo and doacoes < 45:
            valor = float(rnd.choice(TICKETS))
            if soma + valor > alvo * 1.08:      # não estoura muito a meta
                break
            pix_alvo.append((c, valor))
            soma += valor
            doacoes += 1
        c["arrecadado"] = round(soma, 2)        # o UPDATE final confirma pela soma real
    # doações avulsas (sem campanha), direto para a ONG
    for o in ongs:
        for _ in range(rnd.choices([0, 1, 2, 3], weights=[55, 25, 13, 7])[0]):
            pix_alvo.append((None, round(rnd.choice([10, 20, 25, 30, 50, 100, 150, 200]) * 1.0, 2), o))
    pix_ids = ids.novo("doacao_financeira", len(pix_alvo))
    for i, alvo in enumerate(pix_alvo):
        if alvo[0] is not None:
            c, valor = alvo[0], alvo[1]
            o, camp_id = c["ong"], c["id"]
            quando = data_entre(datetime.combine(c["inicio"], datetime.min.time()) + timedelta(hours=9),
                                min(datetime.combine(c["fim"], datetime.min.time()), HOJE), rnd)
        else:
            valor, o, camp_id = alvo[1], alvo[2], None
            quando = data_entre(datetime(2025, 4, 1), HOJE, rnd)
        d = rnd.choice(doadores)
        linhas_pix.append((pix_ids[i], f"PIX{rnd.randint(10**9, 10**10 - 1)}", quando,
                           d["id"], d["nome"], o["id"], o["nome"], "CONFIRMADO", valor, camp_id))
    inserir(cur, "doacao_financeira",
            ["id", "codigo_pix", "data_criacao", "doador_id", "doador_nome", "ong_id",
             "ong_nome", "status", "valor", "campanha_id"], linhas_pix)
    registrar("doacao_financeira", pix_ids)
    print(f"Doacoes PIX: {len(linhas_pix)}  (total R$ {sum(l[8] for l in linhas_pix):,.2f})")
    checkpoint()

    # ---------------- Itens que o doador cadastrou ------------------------
    linhas_doacao = []
    escolhidos = rnd.sample(doadores, min(len(doadores), 900))
    doa_ids = ids.novo("doacao", sum(1 for _ in escolhidos) * 2)
    pos = 0
    for d in escolhidos:
        for _ in range(rnd.randint(1, 2)):
            nome, categoria, desc = rnd.choice(C.ITENS_DOACAO)
            linhas_doacao.append((doa_ids[pos], nome[:150], desc[:255], rnd.randint(1, 12),
                                  categoria, "DOACAO", 1 if rnd.random() < 0.2 else 0,
                                  1 if rnd.random() < 0.35 else 0, d["id"]))
            pos += 1
    inserir(cur, "doacao",
            ["id", "nome", "descricao", "quantidade", "categoria", "tipo", "urgente", "novo",
             "doador_id"], linhas_doacao)
    registrar("doacao", [l[0] for l in linhas_doacao])
    print(f"Itens de doacao cadastrados: {len(linhas_doacao)}")

    # ---------------- Mural de atividades ---------------------------------
    linhas_at = []
    amostra = rnd.sample(concluidos, min(len(concluidos), 500))
    at_ids = ids.novo("atividade", len(amostra) + min(len(campanhas), 300))
    pos = 0
    for it in amostra:
        linhas_at.append((at_ids[pos], "PRESTACAO",
                          f"{it['nec']['ong']['nome']} prestou contas de \"{it['nec']['titulo']}\""[:500],
                          it["nec"]["ong"]["id"], it["nec"]["ong"]["nome"][:255],
                          it["conclusao"]))
        pos += 1
    for c in rnd.sample(campanhas, min(len(campanhas), 300)):
        linhas_at.append((at_ids[pos], "CAMPANHA",
                          f"{c['ong']['nome']} lançou a campanha \"{c['titulo']}\""[:500],
                          c["ong"]["id"], c["ong"]["nome"][:255],
                          datetime.combine(c["inicio"], datetime.min.time()) + timedelta(hours=10)))
        pos += 1
    inserir(cur, "atividade",
            ["id", "tipo", "descricao", "ong_id", "ong_nome", "data_criacao"], linhas_at)
    registrar("atividade", [l[0] for l in linhas_at])
    print(f"Atividades do mural: {len(linhas_at)}")

    # ---------------- Contas da feira (escritas à mão) ---------------------
    montar_contas_demo(cur, ids, rnd, registrar, doadores)
    checkpoint()

    # ---------------- Denormalizados --------------------------------------
    print("Recalculando notas e agregados...")
    cur.execute("""
        UPDATE ong o SET
          nota_media = COALESCE((SELECT ROUND(AVG(a.nota), 1) FROM avaliacao a WHERE a.ong_id = o.id), o.nota_media),
          total_avaliacoes = GREATEST(o.total_avaliacoes,
                                      (SELECT COUNT(*) FROM avaliacao a WHERE a.ong_id = o.id))
    """)
    cur.execute("""
        UPDATE usuario u SET
          nota_media_doador = (SELECT ROUND(AVG(ad.nota), 1) FROM avaliacao_doador ad WHERE ad.doador_id = u.id),
          total_avaliacoes_doador = (SELECT COUNT(*) FROM avaliacao_doador ad WHERE ad.doador_id = u.id)
        WHERE u.tipo = 'DOADOR'
          AND EXISTS (SELECT 1 FROM avaliacao_doador ad WHERE ad.doador_id = u.id)
    """)
    cur.execute("""
        UPDATE campanha c SET valor_arrecadado = COALESCE(
          (SELECT SUM(d.valor) FROM doacao_financeira d WHERE d.campanha_id = c.id), c.valor_arrecadado)
    """)

    MANIFESTO.parent.mkdir(parents=True, exist_ok=True)
    MANIFESTO.write_text(json.dumps(manifesto, indent=2), encoding="utf-8")

    if a.dry_run:
        con.rollback()
        print("\nDRY-RUN: tudo desfeito (rollback).")
    else:
        con.commit()
        print("\nPronto. Manifesto salvo em", MANIFESTO)
    resumo(cur)
    con.close()


def causa_por_nome(nome, rnd):
    """Descobre a causa de uma ONG antiga pelo nome, em vez de sortear."""
    n = sem_acento(nome or "").lower()
    pistas = [
        (("idos", "lar viva", "senior", "vovo", "avos", "terceira idade"), "idosos"),
        (("crianc", "infant", "jovem", "juvenil", "peque", "semente"), "criancas"),
        (("patinha", "pata", "animal", "animais", "bicho", "cao", "gato", "focinho"), "animais"),
        (("mesa", "aliment", "fome", "prato", "sopa", "cozinha", "pao"), "alimentacao"),
        (("educa", "escola", "saber", "biblioteca", "leitura", "estud"), "educacao"),
        (("saude", "apoio", "hospital", "paciente", "amparo"), "saude"),
        (("renascer", "rua", "abrigo", "acolh", "passagem", "recome"), "moradia"),
        (("mulher", "maria", "delas", "materna", "gestante"), "mulheres"),
        (("inclu", "defici", "acessi", "especial", "igualdade"), "deficiencia"),
        (("eco", "verde", "ambient", "mata", "rio", "recicl", "planta"), "ambiente"),
        (("trabalho", "emprego", "capacita", "profiss", "renda", "caminho"), "trabalho"),
        (("igreja", "comunidade", "comunitari", "social", "bem-estar", "bem estar",
          "esperanc", "futuro", "cidadao"), "criancas"),
    ]
    for termos, causa_id in pistas:
        if any(t in n for t in termos):
            return next(c for c in C.CAUSAS if c["id"] == causa_id)
    return rnd.choice(C.CAUSAS)


def geocodificar(consulta, cache):
    """Endereço REAL via Nominatim (o mesmo serviço usado pelo painel da ONG).

    O resultado fica em cache no disco: assim o script roda offline na máquina
    da feira e não bate no serviço público mais de uma vez por endereço.
    Devolve (display_name, lat, lon) ou None se não achar / estiver sem rede.
    """
    if consulta in cache:
        v = cache[consulta]
        return (v["nome"], v["lat"], v["lon"]) if v else None
    import time
    import urllib.parse
    import urllib.request
    url = ("https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=br&q="
           + urllib.parse.quote(consulta))
    req = urllib.request.Request(url, headers={"User-Agent": "ConnectONG-Seed/1.0 (projeto escolar)"})
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            dados = json.loads(r.read().decode("utf-8"))
        time.sleep(1.2)                      # política de uso do Nominatim: 1 req/s
    except Exception as e:
        print(f"  (geocodificacao indisponivel para {consulta!r}: {e})")
        return None
    if not dados:
        cache[consulta] = None
        return None
    d = dados[0]
    cache[consulta] = {"nome": d["display_name"], "lat": float(d["lat"]), "lon": float(d["lon"])}
    return (d["display_name"], float(d["lat"]), float(d["lon"]))


# Cadastros deixados pelos testes que não podem aparecer numa apresentação.
# O e-mail/login é PRESERVADO (continuam funcionando); troca-se o que aparece
# na tela. Nada é apagado, para não quebrar o histórico já ligado a eles.
RENOMEAR_ONG = {
    "ONG Teste FECITEC": ("Casa de Apoio Amanhecer",
                          "A Casa de Apoio Amanhecer hospeda gratuitamente pacientes que vêm de "
                          "outras cidades fazer tratamento em Limeira, junto com um acompanhante. "
                          "São 18 leitos, três refeições por dia e transporte até o hospital de "
                          "referência. Funciona desde 2013 com 14 voluntários."),
    "ong do chinelao": ("Instituto Passo Solidário",
                        "O Instituto Passo Solidário arrecada e distribui calçados e roupas para "
                        "famílias em situação de vulnerabilidade em Limeira. Desde 2018 já foram "
                        "mais de 9 mil pares entregues, com mutirões mensais nos bairros da "
                        "periferia e parceria com escolas da rede pública."),
    "ONG Esperanca": ("Associação Esperança Viva",
                      "A Associação Esperança Viva atende 90 famílias em insegurança alimentar em "
                      "Campinas desde 2016. Monta cestas básicas todo mês, mantém horta "
                      "comunitária e oferece oficina de aproveitamento integral dos alimentos."),
    "Bem-Estar Ong": ("Instituto Bem-Estar Comunitário",
                      "O Instituto Bem-Estar Comunitário atende 140 crianças e adolescentes no "
                      "contraturno escolar em Uberaba, com reforço escolar, oficina de música e "
                      "atividades esportivas. Está em atividade desde 2008 e depende de doações "
                      "de alimentos e material escolar."),
    "Igreja Bom Senhor": ("Ação Social Bom Pastor",
                          "A Ação Social Bom Pastor é o braço assistencial de uma comunidade "
                          "religiosa de Jundiaí. Distribui cestas básicas a 60 famílias, mantém "
                          "bazar beneficente e oferece reforço escolar gratuito aos sábados."),
}

RENOMEAR_USUARIO = {
    "JWT Teste": "Marina Albuquerque",
    "Audit Teste": "Rogério Sampaio",
    "B17": "Carla Nogueira",
    "Doador Teste": "Eduardo Vilela",
    "APAE": "Fernanda Quirino",
    "Gabriel": "Gabriel Chinelatto",
}


def arrumar_cadastros_de_teste(cur, rnd):
    """Troca nome/descrição dos cadastros criados durante os testes.

    Eles continuam existindo (com o mesmo id, e-mail e senha, para não quebrar
    nada), mas deixam de aparecer como "ONG Teste FECITEC" na hora da feira.
    """
    trocas = 0
    for antigo, (novo, descricao) in RENOMEAR_ONG.items():
        cur.execute("SELECT id FROM ong WHERE nome = %s", (antigo,))
        for (oid,) in cur.fetchall():
            cur.execute("UPDATE ong SET nome=%s, descricao=%s, verificada=1 WHERE id=%s",
                        (novo, descricao, oid))
            cur.execute("UPDATE usuario SET nome=%s WHERE ong_id=%s", (novo, oid))
            cur.execute("UPDATE atividade SET ong_nome=%s WHERE ong_id=%s", (novo, oid))
            cur.execute("UPDATE doacao_financeira SET ong_nome=%s WHERE ong_id=%s", (novo, oid))
            trocas += 1
    for antigo, novo in RENOMEAR_USUARIO.items():
        cur.execute("SELECT id FROM usuario WHERE nome = %s AND tipo <> 'ADMIN'", (antigo,))
        for (uid,) in cur.fetchall():
            cur.execute("UPDATE usuario SET nome=%s WHERE id=%s", (novo, uid))
            cur.execute("UPDATE avaliacao SET doador_nome=%s WHERE doador_id=%s", (novo, uid))
            cur.execute("UPDATE doacao_financeira SET doador_nome=%s WHERE doador_id=%s", (novo, uid))
            trocas += 1
    # contas antigas sem telefone/cidade ficam com cara de cadastro abandonado
    cur.execute("SELECT id, cidade FROM usuario WHERE tipo='DOADOR' AND data_exclusao IS NULL "
                "AND (telefone IS NULL OR telefone = '')")
    for uid, cid in cur.fetchall():
        cur.execute("UPDATE usuario SET telefone=%s, cidade=COALESCE(NULLIF(cidade,''),'Limeira'), "
                    "estado=COALESCE(NULLIF(estado,''),'SP') WHERE id=%s",
                    (telefone(19, rnd), uid))
    print(f"Cadastros de teste renomeados: {trocas}")


# Necessidades criadas durante os testes, que aparecem na conta da feira com
# título de rascunho ("batata doce", "chinelos", "Fralda"). Reescritas com
# texto de verdade; o id é preservado, então o histórico continua ligado a elas.
NECESSIDADES_LEGADAS = {
    "batata doce": ("Hortifrúti para as cestas da semana", "Alimentos",
                    "Recebemos doação de legumes e verduras às terças e distribuímos no mesmo dia, sem estoque."),
    "chinelos": ("Chinelos e calçados adultos", "Roupas",
                 "Chinelos e calçados dos números 36 ao 44. É o item que mais falta nos mutirões dos bairros."),
    "Cestas basicas": ("Cestas básicas para as famílias cadastradas", "Alimentos",
                       "Arroz, feijão, macarrão, óleo, açúcar e sal para montar as cestas entregues todo mês."),
    "Agasalhos de inverno": ("Agasalhos de inverno", "Roupas",
                             "Casacos, blusas de moletom e mantas, adulto e infantil, para a campanha de inverno."),
    "Material escolar": ("Material escolar para o ano letivo", "Educacao",
                         "Cadernos, lápis, canetas e mochilas para os kits entregues na primeira semana de aula."),
    "Livros": ("Livros para a biblioteca comunitária", "Educacao",
               "Literatura infantil, juvenil e didáticos em bom estado para a sala de leitura."),
    "Brinquedos": ("Brinquedos e jogos pedagógicos", "Brinquedos",
                   "Jogos de encaixe, quebra-cabeças e bonecos completos para a sala de atividades."),
    "Fraldas": ("Fraldas infantis", "Higiene",
                "Fraldas dos tamanhos M e G para as famílias com crianças pequenas atendidas pela casa."),
    "Fralda": ("Fraldas geriátricas (reposição)", "Higiene",
               "Reposição do estoque de fraldas geriátricas tamanho G, o item de maior consumo da casa."),
    "Cobertores": ("Cobertores e mantas", "Roupas",
                   "Cobertores de solteiro e casal para os leitos durante o inverno."),
    "Brinquedos educativos": ("Brinquedos educativos", "Brinquedos",
                              "Jogos de montar e material lúdico para as turmas do contraturno."),
    "Leite em po": ("Leite em pó para o lanche", "Alimentos",
                    "Latas de 400g de leite em pó integral para o lanche servido todos os dias úteis."),
    "Racao para caes": ("Ração para cães adultos", "Alimentos",
                        "Ração seca para cães adultos. Aceitamos qualquer marca, inclusive pacote já aberto."),
    "Medicamentos veterinarios": ("Medicamentos veterinários", "Saude",
                                  "Vermífugo, antipulgas e medicação de uso comum nos animais resgatados."),
    "Roupas adultas": ("Roupas adultas masculinas e femininas", "Roupas",
                       "Calças, camisetas e casacos tamanhos M, G e GG para o atendimento diário."),
    "Fraldas para Idosos com Dignidade": ("Fraldas geriátricas para os residentes acamados", "Higiene",
                                          "Fraldas tamanho G para os residentes acamados, principal item de consumo da casa."),
}


def arrumar_necessidades_legadas(cur):
    """Reescreve as necessidades de teste e tira as duplicatas de interesse.

    Sem isso, a tela "Meus interesses" da conta da feira abre mostrando
    "batata doce", "chinelos" e quatro vezes o mesmo "Material escolar" -
    exatamente o tipo de coisa que denuncia banco de teste.
    """
    ajustadas = 0
    for antigo, (novo, categoria, desc) in NECESSIDADES_LEGADAS.items():
        cur.execute("UPDATE necessidade SET titulo=%s, categoria=%s, descricao=%s, status='ATENDIDA' "
                    "WHERE titulo=%s", (novo, categoria, desc, antigo))
        ajustadas += cur.rowcount
    # categorias antigas em minúsculo/singular ("alimento", "roupas") viram as
    # canônicas do backend
    for canonica in C.CAUSAS[0]["categorias"] + ["Saude", "Educacao", "Brinquedos"]:
        cur.execute("UPDATE necessidade SET categoria=%s WHERE LOWER(categoria) IN (%s, %s)",
                    (canonica, canonica.lower(), canonica.lower().rstrip("s")))
    # o mesmo doador demonstrando interesse 4x na mesma necessidade é resíduo
    # de teste: fica o mais recente
    cur.execute("""SELECT doador_id, necessidade_id, COUNT(*) q, MAX(id) manter
                   FROM interesse GROUP BY doador_id, necessidade_id HAVING q > 1""")
    duplicados = cur.fetchall()
    removidos = 0
    for doador, nec, _q, manter in duplicados:
        cur.execute("SELECT id FROM interesse WHERE doador_id=%s AND necessidade_id=%s AND id<>%s",
                    (doador, nec, manter))
        ids_fora = [x[0] for x in cur.fetchall()]
        for i in ids_fora:
            cur.execute("DELETE FROM prestacao WHERE interesse_id=%s", (i,))
            cur.execute("DELETE FROM mensagem WHERE interesse_id=%s", (i,))
            cur.execute("DELETE FROM interesse WHERE id=%s", (i,))
            removidos += 1
    print(f"Necessidades de teste reescritas: {ajustadas} | interesses duplicados removidos: {removidos}")


def enriquecer_existentes(cur, por_uf, rnd):
    """Completa as ONGs que já estavam no banco: endereço, coordenada, selo e CNPJ.

    Não sobrescreve descrição nem nome (as contas de demonstração da feira
    precisam continuar reconhecíveis); só preenche o que está vazio.
    """
    cur.execute("SELECT id, nome, cidade, endereco, latitude, cnpj, descricao "
                "FROM ong WHERE data_exclusao IS NULL AND (endereco IS NULL OR latitude IS NULL)")
    pendentes = cur.fetchall()
    todos = [m for lista in por_uf.values() for m in lista]
    indice = {sem_acento(m["nome"]).lower(): m for m in todos}
    ajustes = 0
    for oid, nome, cidade, endereco, lat, doc, desc in pendentes:
        cidade_limpa = (cidade or "Limeira").split(" - ")[0].strip()
        m = indice.get(sem_acento(cidade_limpa).lower()) or indice["limeira"]
        bairro = rnd.choice(C.BAIRROS)
        novo_end = endereco or endereco_de(m, bairro, rnd)
        la, lo = coordenada(m, rnd)
        cur.execute("UPDATE ong SET endereco=%s, latitude=%s, longitude=%s, cidade=%s, "
                    "cnpj=COALESCE(cnpj,%s) WHERE id=%s",
                    (novo_end, la, lo, f"{m['nome']} - {m['uf']}"[:50], cnpj(rnd), oid))
        ajustes += 1
    print(f"ONGs antigas completadas (endereco/coordenada): {ajustes}")


def montar_contas_demo(cur, ids, rnd, registrar, doadores):
    """Escreve à mão a história das contas que vão para o telão.

    Diferente do resto do banco (que é gerado por combinação), aqui cada
    necessidade, conversa e prestação foi redigida para fazer sentido junto: a
    conversa fala do item que foi doado, e a prestação de contas conta o
    destino daquele item. Também deixa PENDÊNCIAS de propósito, para haver o
    que aceitar/demonstrar ao vivo durante a apresentação.
    """
    cache_arq = DADOS / "cache_enderecos.json"
    cache = json.loads(cache_arq.read_text(encoding="utf-8")) if cache_arq.exists() else {}

    def usuario_por_email(email):
        cur.execute("SELECT id, nome FROM usuario WHERE email = %s", (email,))
        return cur.fetchone()

    def ong_por_email(email):
        cur.execute("SELECT id, nome FROM ong WHERE email = %s", (email,))
        return cur.fetchone()

    # ---- perfis dos doadores de demonstração ----
    doadores_demo = {}
    for email, p in D.DOADORES_DEMO.items():
        u = usuario_por_email(email)
        if not u:
            continue
        cur.execute("UPDATE usuario SET nome=%s, cidade=%s, estado=%s, telefone=%s, bio=%s, "
                    "ultimo_visto=%s WHERE id=%s",
                    (p["nome"], p["cidade"], p["uf"], p["telefone"], p["bio"],
                     HOJE - timedelta(minutes=rnd.randint(5, 90)), u[0]))
        doadores_demo[email] = {"id": u[0], "nome": p["nome"]}

    # ---- perfis das ONGs de demonstração ----
    ongs_demo, nec_por_ong = {}, {}
    for email, p in D.ONGS_DEMO.items():
        o = ong_por_email(email)
        if not o:
            print(f"  (ONG de demonstracao ausente: {email})")
            continue
        oid = o[0]
        consulta = f"{p['via']}, {p['cidade']}, {p['uf']}, Brasil"
        achado = geocodificar(consulta, cache)
        if achado:
            _, lat, lon = achado
            print(f"  endereco real confirmado: {consulta} -> {lat}, {lon}")
        else:
            lat, lon = None, None
        endereco = f"{p['via']}, {p['numero']} - {p['bairro']}, {p['cidade']} - {p['uf']}"
        cur.execute("UPDATE ong SET nome=%s, descricao=%s, telefone=%s, cnpj=COALESCE(cnpj,%s), "
                    "verificada=%s, endereco=%s, cidade=%s, "
                    "latitude=COALESCE(%s, latitude), longitude=COALESCE(%s, longitude) "
                    "WHERE id=%s",
                    (p["nome"], p["descricao"], p["telefone"], cnpj(rnd), p["verificada"],
                     endereco[:255], f"{p['cidade']} - {p['uf']}"[:50], lat, lon, oid))
        ongs_demo[email] = {"id": oid, "nome": p["nome"], "endereco": endereco}

        # necessidades antigas viram histórico (não poluem o feed da feira)
        cur.execute("UPDATE necessidade SET status='ATENDIDA' WHERE ong_id=%s", (oid,))
        cur.execute("UPDATE necessidade SET titulo=%s, categoria='Educacao', "
                    "descricao=%s WHERE ong_id=%s AND titulo='Brinquedos Digitais'",
                    ("Tablets para a oficina de memória", "Usamos jogos de memória em tablet nas "
                     "oficinas das quintas-feiras. Aceitamos aparelhos usados que ainda liguem.", oid))

        novas = ids.novo("necessidade", len(p["necessidades"]))
        linhas, mapa = [], {}
        for i, (titulo, categoria, status, urgente, desc) in enumerate(p["necessidades"]):
            criada = (HOJE - timedelta(days=rnd.randint(3, 60)) if status == "ABERTA"
                      else HOJE - timedelta(days=rnd.randint(70, 210)))
            linhas.append((novas[i], categoria, criada, desc[:255], status, titulo[:150],
                           urgente, oid))
            mapa[titulo] = novas[i]
        inserir(cur, "necessidade",
                ["id", "categoria", "data_criacao", "descricao", "status", "titulo", "urgente",
                 "ong_id"], linhas)
        registrar("necessidade", novas)
        nec_por_ong[email] = mapa

        # campanhas com as doações PIX que as sustentam
        camp_ids = ids.novo("campanha", len(p["campanhas"]))
        linhas_c, pix = [], []
        for i, (titulo, categoria, meta, encerrada, desc) in enumerate(p["campanhas"]):
            dur = rnd.choice([45, 60, 90])
            fim = (HOJE - timedelta(days=rnd.randint(8, 60))).date() if encerrada \
                else (HOJE + timedelta(days=rnd.randint(12, 45))).date()
            inicio = fim - timedelta(days=dur)
            alvo = meta * (rnd.uniform(1.0, 1.18) if encerrada else rnd.uniform(0.25, 0.7))
            soma = 0.0
            while soma < alvo and len(pix) < 400:
                v = float(rnd.choice([20, 25, 30, 50, 50, 75, 100, 100, 150, 200, 250]))
                if soma + v > alvo * 1.05:
                    break
                d = rnd.choice(doadores)
                quando = data_entre(datetime.combine(inicio, datetime.min.time()) + timedelta(hours=9),
                                    min(datetime.combine(fim, datetime.min.time()), HOJE), rnd)
                pix.append((camp_ids[i], v, d, quando))
                soma += v
            linhas_c.append((camp_ids[i], desc[:255], meta, titulo[:150], oid, round(soma, 2),
                             categoria, inicio, fim, 1 if encerrada else 0,
                             1 if i == 0 else 0))
        inserir(cur, "campanha",
                ["id", "descricao", "meta_valor", "titulo", "ong_id", "valor_arrecadado",
                 "categoria", "data_inicio", "data_fim", "encerrada", "destaque"], linhas_c)
        registrar("campanha", camp_ids)
        pix_ids = ids.novo("doacao_financeira", len(pix))
        linhas_pix = [(pix_ids[j], f"PIX{rnd.randint(10**9, 10**10 - 1)}", quando, d["id"],
                       d["nome"], oid, p["nome"], "CONFIRMADO", v, cid)
                      for j, (cid, v, d, quando) in enumerate(pix)]
        inserir(cur, "doacao_financeira",
                ["id", "codigo_pix", "data_criacao", "doador_id", "doador_nome", "ong_id",
                 "ong_nome", "status", "valor", "campanha_id"], linhas_pix)
        registrar("doacao_financeira", pix_ids)

    # ---- as conversas ----
    def criar_conversa(ong_email, titulo_nec, dias_atras, roteiro, status, doador):
        nec_id = nec_por_ong.get(ong_email, {}).get(titulo_nec)
        if not nec_id or ong_email not in ongs_demo:
            return None
        criada = HOJE - timedelta(days=dias_atras, hours=rnd.randint(0, 8))
        data_status = criada + timedelta(hours=rnd.randint(2, 30)) if status != "PENDENTE" else None
        conclusao = None
        if status == "CONCLUIDO":
            conclusao = data_status + timedelta(days=rnd.randint(2, 9))
        it_id = ids.novo("interesse")
        inserir(cur, "interesse",
                ["id", "data_criacao", "status", "doador_id", "necessidade_id",
                 "data_conclusao", "data_status"],
                [(it_id, criada, status, doador["id"], nec_id, conclusao, data_status)])
        registrar("interesse", [it_id])

        quando = data_status or criada
        msg_ids = ids.novo("mensagem", len(roteiro))
        linhas_m = []
        for j, (remetente, texto) in enumerate(roteiro):
            quando = quando + timedelta(minutes=rnd.randint(4, 240))
            corpo = texto.format(item=titulo_nec.lower(), endereco=ongs_demo[ong_email]["endereco"])
            # a última mensagem de uma conversa em aberto fica NÃO LIDA
            lida = None if (status != "CONCLUIDO" and j == len(roteiro) - 1) \
                else quando + timedelta(minutes=rnd.randint(1, 120))
            linhas_m.append((msg_ids[j], corpo[:1000], quando, remetente, it_id, lida, None, None))
        inserir(cur, "mensagem",
                ["id", "conteudo", "data_envio", "remetente", "interesse_id", "data_leitura",
                 "anexo_base64", "anexo_tipo"], linhas_m)
        registrar("mensagem", msg_ids)
        return {"id": it_id, "conclusao": conclusao, "titulo": titulo_nec,
                "ong": ongs_demo[ong_email], "doador": doador}

    joao = doadores_demo.get("demo.joao@connectong.com")
    concluidos_demo = []
    if joao:
        for ong_email, titulo, dias, roteiro in D.HISTORICO_JOAO:
            it = criar_conversa(ong_email, titulo, dias, roteiro, "CONCLUIDO", joao)
            if it:
                concluidos_demo.append(it)
        for ong_email, titulo, dias, roteiro in D.EM_ANDAMENTO_JOAO:
            criar_conversa(ong_email, titulo, dias, roteiro, "ACEITO", joao)

    for ong_email_doador, titulo, dias, status, roteiro in D.HISTORICO_LARVIVA:
        d = doadores_demo.get(ong_email_doador)
        if d:
            it = criar_conversa("demo.larviva@connectong.com", titulo, dias, roteiro, status, d)
            if it and status == "CONCLUIDO":
                concluidos_demo.append(it)
    for email_doador, titulo, dias, roteiro in D.PENDENTES_LARVIVA:
        d = doadores_demo.get(email_doador)
        if d:
            criar_conversa("demo.larviva@connectong.com", titulo, dias, roteiro, "PENDENTE", d)

    # ---- prestações de contas casadas com o que foi doado ----
    textos = dict(D.PRESTACOES_LARVIVA)
    textos.update(D.PRESTACOES_OUTRAS)
    alvo = [it for it in concluidos_demo if it["titulo"] in textos]
    p_ids = ids.novo("prestacao", len(alvo))
    linhas_p = []
    for i, it in enumerate(alvo):
        titulo, desc = textos[it["titulo"]]
        linhas_p.append((p_ids[i], it["conclusao"] + timedelta(days=rnd.randint(1, 6)),
                         desc[:1000], None, titulo[:150], it["id"],
                         round(rnd.uniform(120, 900), 2) if rnd.random() < 0.4 else None))
    inserir(cur, "prestacao",
            ["id", "data_criacao", "descricao", "foto_url", "titulo", "interesse_id",
             "valor_utilizado"], linhas_p)
    registrar("prestacao", p_ids)

    # ---- avaliações recebidas pelas ONGs de demonstração ----
    linhas_av, total_av = [], 0
    for email, info in ongs_demo.items():
        causa = causa_por_nome(info["nome"], rnd)
        quantas = rnd.randint(22, 38)
        av_ids = ids.novo("avaliacao", quantas)
        for i in range(quantas):
            d = rnd.choice(doadores)
            nota = rnd.choices([5, 4, 3], weights=[84, 14, 2])[0]
            quando = data_entre(datetime(2025, 4, 1), HOJE - timedelta(days=2), rnd)
            linhas_av.append((av_ids[i], rnd.choice(causa["avaliacoes"])[:500], quando,
                              d["id"], d["nome"], nota, info["id"]))
        registrar("avaliacao", av_ids)
        total_av += quantas
    inserir(cur, "avaliacao",
            ["id", "comentario", "data_criacao", "doador_id", "doador_nome", "nota", "ong_id"],
            linhas_av)

    # ---- reputação da conta do doador (a ONG avalia quem doou) ----
    if joao:
        alvos = [it for it in concluidos_demo if it["doador"]["id"] == joao["id"]][:5]
        avd_ids = ids.novo("avaliacao_doador", len(alvos))
        linhas_avd, vistos = [], set()
        for i, it in enumerate(alvos):
            if it["ong"]["id"] in vistos:
                continue
            vistos.add(it["ong"]["id"])
            quando = it["conclusao"] + timedelta(days=rnd.randint(1, 8))
            linhas_avd.append((avd_ids[i], joao["id"], it["ong"]["id"], 5,
                               D.AVALIACOES_DE_JOAO[i % len(D.AVALIACOES_DE_JOAO)][:500],
                               quando, quando))
        inserir(cur, "avaliacao_doador",
                ["id", "doador_id", "ong_id", "nota", "comentario", "criado_em", "atualizado_em"],
                linhas_avd)
        registrar("avaliacao_doador", [l[0] for l in linhas_avd])

        # PIX da conta da feira: garante as conquistas de "10 contribuições"
        alvos_pix = [o for o in ongs_demo.values()]
        pix_ids = ids.novo("doacao_financeira", 11)
        linhas_pix = []
        for i in range(11):
            o = alvos_pix[i % len(alvos_pix)]
            linhas_pix.append((pix_ids[i], f"PIX{rnd.randint(10**9, 10**10 - 1)}",
                               data_entre(datetime(2025, 5, 1), HOJE - timedelta(days=1), rnd),
                               joao["id"], joao["nome"], o["id"], o["nome"], "CONFIRMADO",
                               float(rnd.choice([20, 25, 30, 50, 50, 75, 100, 150])), None))
        inserir(cur, "doacao_financeira",
                ["id", "codigo_pix", "data_criacao", "doador_id", "doador_nome", "ong_id",
                 "ong_nome", "status", "valor", "campanha_id"], linhas_pix)
        registrar("doacao_financeira", pix_ids)

        # notificações (o sino abre aceso, como numa conta em uso)
        n_ids = ids.novo("notificacao", len(D.NOTIFICACOES_JOAO))
        linhas_n = [(n_ids[i], HOJE - timedelta(hours=h), lida, msg[:400], tipo, tit)
                    for i, (tipo, tit, msg, lida, h) in enumerate(D.NOTIFICACOES_JOAO)]
        inserir(cur, "notificacao",
                ["id", "data_criacao", "lida", "mensagem", "tipo", "titulo"],
                [(l[0], l[1], l[2], l[3], l[4], l[5]) for l in linhas_n])
        cur.executemany("UPDATE notificacao SET usuario_id=%s WHERE id=%s",
                        [(joao["id"], l[0]) for l in linhas_n])
        registrar("notificacao", n_ids)

    lar = ong_por_email("demo.larviva@connectong.com")
    if lar:
        cur.execute("SELECT id FROM usuario WHERE ong_id = %s", (lar[0],))
        conta = cur.fetchone()
        if conta:
            n_ids = ids.novo("notificacao", len(D.NOTIFICACOES_LARVIVA))
            linhas_n = [(n_ids[i], HOJE - timedelta(hours=h), lida, msg[:400], tipo, tit,
                         conta[0])
                        for i, (tipo, tit, msg, lida, h) in enumerate(D.NOTIFICACOES_LARVIVA)]
            inserir(cur, "notificacao",
                    ["id", "data_criacao", "lida", "mensagem", "tipo", "titulo", "usuario_id"],
                    linhas_n)
            registrar("notificacao", n_ids)
        # a Lar Viva é a #1 do ranking de transparência há algumas semanas
        cur.execute("UPDATE ong SET top1_desde=%s, ultimo_reinado_dias=%s WHERE id=%s",
                    (HOJE - timedelta(days=23), 41, lar[0]))

    cache_arq.write_text(json.dumps(cache, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"Contas da feira: {len(ongs_demo)} ONGs e {len(doadores_demo)} doadores com historico "
          f"proprio ({len(concluidos_demo)} conversas concluidas, {len(linhas_p)} prestacoes, "
          f"{total_av} avaliacoes)")


def limpar(con, cur):
    """Remove SOMENTE o que este script criou, na ordem inversa das dependências."""
    if not MANIFESTO.exists():
        sys.exit("Nao ha manifesto: nada para limpar.")
    faixas = json.loads(MANIFESTO.read_text(encoding="utf-8"))["faixas"]
    ordem = ["atividade", "doacao", "doacao_financeira", "avaliacao_doador", "avaliacao",
             "prestacao", "mensagem", "interesse", "campanha", "necessidade", "usuario", "ong"]
    for tabela in ordem:
        if tabela not in faixas:
            continue
        ini, fim = faixas[tabela]
        cur.execute(f"DELETE FROM `{tabela}` WHERE id BETWEEN %s AND %s", (ini, fim))
        print(f"{tabela}: {cur.rowcount} linhas removidas")
    checkpoint()
    print("Limpeza concluida.")


def resumo(cur):
    cur.execute("""
        SELECT (SELECT COUNT(*) FROM ong WHERE data_exclusao IS NULL),
               (SELECT COUNT(*) FROM usuario WHERE tipo='DOADOR' AND data_exclusao IS NULL),
               (SELECT COUNT(*) FROM necessidade),
               (SELECT COUNT(*) FROM necessidade WHERE status='ABERTA'),
               (SELECT COUNT(*) FROM interesse WHERE status='ACEITO'),
               (SELECT COUNT(*) FROM doacao_financeira),
               (SELECT COALESCE(SUM(valor),0) FROM doacao_financeira),
               (SELECT COUNT(*) FROM prestacao)
    """)
    o, d, n, na, m, pix, valor, pr = cur.fetchone()
    print(f"""
=== NUMEROS PUBLICOS DA PLATAFORMA ===
  ONGs cadastradas ....... {o}
  Doadores ............... {d}
  Necessidades ........... {n}  (abertas agora: {na})
  Matches aceitos ........ {m}
  Doacoes PIX ............ {pix}   (R$ {valor:,.2f})
  Prestacoes de contas ... {pr}
""")


if __name__ == "__main__":
    main()
