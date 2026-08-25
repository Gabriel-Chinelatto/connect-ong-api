# -*- coding: utf-8 -*-
"""Ilustra a demonstracao INTEIRA: logo + capa em TODAS as ONGs e foto de perfil
em TODOS os doadores.

Diferenca para o subir_fotos.py (que continua existindo): aquele coloca uma foto
ESCOLHIDA A DEDO numa ONG especifica. Este aqui cobre as 2.000 ONGs de uma vez,
escolhendo a imagem pela CAUSA de cada instituicao (a mesma heuristica de nome
que o seed_demo usa), para o visitante da feira nunca abrir um perfil vazio.

De onde vem cada imagem
-----------------------
* capa   : foto real de licenca livre (Openverse/Wikimedia), uma pasta por causa,
           ja recortada em 16:9 e comprimida (~35 KB).
* logo   : desenho AUTORAL — disco na cor da causa + pictograma Material Icons
           (Apache 2.0). Nao e marca de ninguem, entao nao ha questao de direito.
* doador : retrato de randomuser.me, escolhido pelo SEXO do primeiro nome, para
           a foto combinar com o nome que aparece ao lado.

Por que gera um .sql em vez de so gravar no banco
-------------------------------------------------
O RESTAURAR-DEMO.bat reimporta o dump da escola entre uma apresentacao e outra —
e o dump NAO tem imagem nenhuma. Sem um jeito rapido de repor, as fotos sumiriam
na primeira restauracao. O .sql gerado agrupa as ONGs que compartilham a mesma
imagem num unico "UPDATE ... WHERE id IN (...)": sao ~90 comandos no lugar de
3.200, o arquivo fica em ~4 MB (nao ~150 MB) e a reposicao leva segundos.

Uso
---
    python ferramentas/ilustrar_demo.py --imagens <pasta> --host 127.0.0.1 \
        --usuario feira --senha feira123
    python ferramentas/ilustrar_demo.py --imagens <pasta> --sql saida.sql --so-sql

A <pasta> de imagens precisa ter:
    capa/<causa>-<n>.jpg      logo/<causa>-<n>.png      rosto/{m,f}/<nn>.jpg
"""
import argparse
import base64
import io
import os
import pathlib
import re
import sys
import unicodedata
from collections import defaultdict

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from dados_demo import conteudo as C          # noqa: E402
from seed_demo import causa_por_nome, conectar, sem_acento  # noqa: E402

# Capas curadas a mao (as 6 ONGs que aparecem no telao). Estas NAO sao trocadas
# pela imagem generica da causa — ver --forcar.
CURADAS = os.path.join("capa-curada")


def como_data_uri(caminho):
    tipo = "image/png" if str(caminho).lower().endswith(".png") else "image/jpeg"
    dados = pathlib.Path(caminho).read_bytes()
    return f"data:{tipo};base64," + base64.b64encode(dados).decode()


def catalogo(pasta, sub, extensao):
    """{causa: [data-uri, ...]} lido de <pasta>/<sub>/<causa>-<n>.<ext>."""
    saida = defaultdict(list)
    raiz = pathlib.Path(pasta) / sub
    for arq in sorted(raiz.glob(f"*{extensao}")):
        causa = arq.stem.rsplit("-", 1)[0]
        saida[causa].append(como_data_uri(arq))
    return saida


def retratos(pasta):
    saida = {}
    for sexo in ("m", "f"):
        raiz = pathlib.Path(pasta) / "rosto" / sexo
        saida[sexo] = [como_data_uri(a) for a in sorted(raiz.glob("*.jpg"))]
    return saida


def normalizar(nome):
    return re.sub(r"[^a-z ]+", " ", sem_acento(nome or "").lower()).strip()


# nucleo do nome -> causa. O seed monta TODO nome de ONG a partir de um "nucleo"
# da causa ("Lar Viva", "Abrigo Patinhas", "Semente do Amanha"...), entao esta e
# a classificacao EXATA.
NUCLEOS = sorted(
    ((normalizar(n), c["id"]) for c in C.CAUSAS for n in c["nucleos"]),
    key=lambda x: -len(x[0]),
)


# As ONGs cadastradas A MAO (antes da massa gerada) nao seguem o padrao de
# nucleo do seed, e a heuristica por pedaco de palavra erra feio nelas:
# "Acao Social" e "Fundacao" caem em ANIMAIS por causa do "cao" de "acao", e
# "Comunitario" cai em AMBIENTE por causa do "rio". Sao poucas e aparecem cedo
# nas listas, entao a causa de cada uma vem escrita aqui, conferida na descricao.
CAUSA_A_MAO = {
    "instituto bem estar comunitario": "criancas",     # contraturno de 140 criancas
    "acao social bom pastor": "alimentacao",           # assistencia de comunidade religiosa
    "instituto esperanca": "educacao",                 # apoio social e educacional
    "casa de apoio amanhecer": "saude",                # hospeda pacientes em tratamento
    "instituto futuro cidadao": "criancas",
    "fundacao tecnologia social": "educacao",          # inclusao digital de estudantes
    "projeto renovacao jovem": "criancas",             # adolescentes, oficinas
    "instituto comunidade ativa": "educacao",          # educacao, cultura e esporte
    "associacao esperanca viva": "alimentacao",        # familias em inseguranca alimentar
    "instituto passo solidario": "moradia",            # roupas/calcados p/ situacao de rua
}


def causa_da_ong(nome, semente):
    """Descobre a causa pelo nucleo do nome; so cai na heuristica por palavra
    solta quando nenhum nucleo bate (ONGs antigas, cadastradas a mao).

    A heuristica sozinha nao serve aqui: ela procura PEDACO de palavra, entao
    "Instituto Bem-Estar Comunitario" caia em "ambiente" por causa do "rio" de
    "comunitario" — e ganhava capa de plantio de arvore.
    """
    limpo = normalizar(nome)
    if limpo in CAUSA_A_MAO:
        return CAUSA_A_MAO[limpo]
    alvo = " " + limpo + " "
    for nucleo, causa in NUCLEOS:
        if " " + nucleo + " " in alvo:
            return causa
    import random
    return causa_por_nome(nome, random.Random(semente))["id"]


def sexo_do_nome(nome, femininos, masculinos):
    """Descobre o sexo pelo PRIMEIRO nome, com as listas do proprio seed.
    Fora das listas, cai na terminacao ('a' = feminino), que acerta a grande
    maioria dos nomes em portugues."""
    primeiro = normalizar(nome).split(" ")[0] if nome else ""
    if primeiro in femininos:
        return "f"
    if primeiro in masculinos:
        return "m"
    return "f" if primeiro.endswith("a") else "m"


class Aplicador:
    """Junta as ONGs/doadores que recebem a MESMA imagem, para sair um UPDATE
    por imagem em vez de um por linha."""

    def __init__(self):
        self.grupos = defaultdict(list)   # (tabela, coluna, imagem) -> [ids]
        self.avulsos = []                 # (tabela, coluna, imagem, id)

    def juntar(self, tabela, coluna, imagem, id_):
        self.grupos[(tabela, coluna, imagem)].append(id_)

    def avulso(self, tabela, coluna, imagem, id_):
        self.avulsos.append((tabela, coluna, imagem, id_))

    def comandos(self):
        for (tabela, coluna, imagem), ids in self.grupos.items():
            alvo = ",".join(str(i) for i in sorted(ids))
            yield f"UPDATE {tabela} SET {coluna}='{imagem}' WHERE id IN ({alvo});"
        for tabela, coluna, imagem, id_ in self.avulsos:
            yield f"UPDATE {tabela} SET {coluna}='{imagem}' WHERE id={id_};"

    def total_linhas(self):
        return sum(len(v) for v in self.grupos.values()) + len(self.avulsos)


# A coluna logo_base64 e nova. O dump da escola (usado pelo RESTAURAR-DEMO) foi
# tirado ANTES dela existir, entao a restauracao apaga a coluna. Este prologo a
# recria quando falta — o "IF NOT EXISTS" de coluna nao existe no MySQL, dai o
# rodeio com information_schema + PREPARE.
PROLOGO = """-- Connect ONG - imagens da demonstracao (gerado por ferramentas/ilustrar_demo.py)
-- Reaplica logo/capa das ONGs e foto dos doadores depois de restaurar o dump.
SET @existe := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'ong' AND COLUMN_NAME = 'logo_base64');
SET @cmd := IF(@existe = 0,
               'ALTER TABLE ong ADD COLUMN logo_base64 MEDIUMTEXT', 'DO 0');
PREPARE st FROM @cmd; EXECUTE st; DEALLOCATE PREPARE st;
-- Tudo numa transacao so: ou a demo fica ilustrada inteira, ou nao muda
-- nada (nada pior do que restaurar no meio da feira e ficar com metade das
-- telas sem imagem). Sao ~80 MB gravados, entao esta parte leva ~30s.
SET autocommit = 0;
"""

EPILOGO = "COMMIT;\n"


def main():
    p = argparse.ArgumentParser(description="Poe logo/capa em todas as ONGs e foto em todos os doadores")
    p.add_argument("--imagens", required=True, help="pasta com capa/, logo/ e rosto/")
    p.add_argument("--sql", help="tambem grava os comandos neste arquivo .sql")
    p.add_argument("--so-sql", action="store_true", help="nao toca no banco, so gera o .sql")
    # O .sql gerado aqui e aplicado DEPOIS de restaurar o dump da escola, que
    # vem sem imagem nenhuma — entao, por padrao, ele precisa trazer a capa de
    # TODAS as ONGs. "Pular quem ja tem capa" so faz sentido quando se esta
    # gravando direto num banco ja ilustrado e se quer preservar o que la esta.
    p.add_argument("--preservar-capa", action="store_true",
                   help="nao troca a capa de quem ja tem uma (as curadas a mao "
                        "sao sempre preservadas, com ou sem esta opcao)")
    p.add_argument("--host", default="143.106.241.3")
    p.add_argument("--porta", type=int, default=3306)
    p.add_argument("--usuario", default="cl203161")
    p.add_argument("--banco", default="cl203161")
    p.add_argument("--senha", default=None)
    a = p.parse_args()

    pasta = pathlib.Path(a.imagens)
    capas = catalogo(pasta, "capa", ".jpg")
    logos = catalogo(pasta, "logo", ".png")
    rostos = retratos(pasta)
    curadas = {}
    if (pasta / CURADAS).exists():
        for arq in sorted((pasta / CURADAS).glob("*.jpg")):
            if arq.stem.isdigit():
                curadas[int(arq.stem)] = como_data_uri(arq)
    if not capas or not logos or not rostos["m"]:
        sys.exit(f"Pasta de imagens incompleta: {pasta} (precisa de capa/, logo/ e rosto/)")

    print(f"catalogo: {sum(len(v) for v in capas.values())} capas, "
          f"{sum(len(v) for v in logos.values())} logos, "
          f"{len(rostos['m'])+len(rostos['f'])} retratos, {len(curadas)} capas curadas")

    con = conectar(a)
    cur = con.cursor()
    ap = Aplicador()

    # ---- ONGs: capa + logo pela causa ----
    from collections import Counter
    contagem = Counter()
    cur.execute("SELECT id, nome, capa_base64 IS NOT NULL AND capa_base64 <> '' "
                "FROM ong WHERE data_exclusao IS NULL")
    ongs = cur.fetchall()
    for oid, nome, tem_capa in ongs:
        causa = causa_da_ong(nome, oid)
        contagem[causa] += 1
        lista_capa = capas.get(causa) or next(iter(capas.values()))
        lista_logo = logos.get(causa) or next(iter(logos.values()))
        # Sorteio ESTAVEL pelo id: a mesma ONG recebe sempre a mesma imagem,
        # entao rodar de novo (ou restaurar a demo) nao embaralha os perfis.
        if oid in curadas:
            # As 6 do telao mantem a foto escolhida a mao, sempre.
            ap.avulso("ong", "capa_base64", curadas[oid], oid)
        elif not (a.preservar_capa and tem_capa):
            ap.juntar("ong", "capa_base64", lista_capa[oid % len(lista_capa)], oid)
        ap.juntar("ong", "logo_base64", lista_logo[oid % len(lista_logo)], oid)

    # ---- Doadores: retrato de acordo com o sexo do primeiro nome ----
    femininos = {normalizar(n) for n in C.NOMES_F}
    masculinos = {normalizar(n) for n in C.NOMES_M}
    cur.execute("SELECT id, nome FROM usuario "
                "WHERE tipo = 'DOADOR' AND data_exclusao IS NULL")
    doadores = cur.fetchall()
    for uid, nome in doadores:
        sexo = sexo_do_nome(nome, femininos, masculinos)
        lista = rostos[sexo] or rostos["m"]
        ap.juntar("usuario", "foto_base64", lista[uid % len(lista)], uid)

    # ---- Contas de ONG: o avatar da conta e o logo da propria ONG ----
    cur.execute("SELECT u.id, o.id, o.nome FROM usuario u JOIN ong o ON o.id = u.ong_id "
                "WHERE u.tipo = 'ONG' AND u.data_exclusao IS NULL")
    contas = cur.fetchall()
    for uid, oid, nome in contas:
        causa = causa_da_ong(nome, oid)
        lista_logo = logos.get(causa) or next(iter(logos.values()))
        ap.juntar("usuario", "foto_base64", lista_logo[oid % len(lista_logo)], uid)

    print("  causas: " + ", ".join(f"{c}={n}" for c, n in contagem.most_common()))

    comandos = list(ap.comandos())
    print(f"{len(ongs)} ONGs, {len(doadores)} doadores, {len(contas)} contas de ONG "
          f"-> {ap.total_linhas()} linhas em {len(comandos)} comandos")

    if a.sql:
        with io.open(a.sql, "w", encoding="utf-8", newline="\n") as f:
            f.write(PROLOGO)
            for c in comandos:
                f.write(c + "\n")
            f.write(EPILOGO)
        print(f"SQL gravado: {a.sql} ({os.path.getsize(a.sql)/1024/1024:.1f} MB)")

    if a.so_sql:
        con.close()
        return

    cur.execute("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                "AND TABLE_NAME = 'ong' AND COLUMN_NAME = 'logo_base64'")
    if cur.fetchone()[0] == 0:
        cur.execute("ALTER TABLE ong ADD COLUMN logo_base64 MEDIUMTEXT")
        print("coluna ong.logo_base64 criada")
    for i, c in enumerate(comandos, 1):
        cur.execute(c)
        if i % 25 == 0:
            print(f"  {i}/{len(comandos)}")
    con.commit()
    con.close()
    print("Pronto: banco ilustrado.")


if __name__ == "__main__":
    main()
