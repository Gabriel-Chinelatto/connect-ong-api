# -*- coding: utf-8 -*-
"""Coloca FOTOS REAIS nas ONGs e nas prestações de contas da demonstração.

O texto do banco o computador escreve; a foto não. Este script pega imagens de
uma pasta do seu computador, reduz o tamanho e grava no banco no formato que o
aplicativo usa (base64), sem você precisar abrir tela nenhuma.

Como usar
---------
1) Instale a biblioteca de imagem (uma vez só):

       pip install pillow

2) Monte a pasta assim (o nome do arquivo diz onde a foto vai):

       fotos/
         capa/33.jpg              -> capa do perfil da ONG de id 33
         capa/lar-viva.jpg        -> idem, achando a ONG pelo NOME
         local/33-1.jpg           -> foto do local da ONG 33 (até 5 por ONG)
         local/33-2.jpg
         prestacao/128.jpg        -> foto da prestação de contas de id 128

3) Rode:

       python ferramentas/subir_fotos.py --pasta fotos
       python ferramentas/subir_fotos.py --pasta fotos --listar   (só mostra o que faria)

Onde arrumar as fotos (uso livre, sem problema de direito autoral):
   https://www.pexels.com   ·   https://unsplash.com   ·   https://pixabay.com
Busque em inglês por: "food donation", "animal shelter", "elderly care",
"volunteers", "school supplies", "clothes donation".

Tamanhos: a capa entra com no máximo 1200px de largura e a foto de local com
900px, ambas em JPEG de qualidade 80. Uma capa fica em torno de 60-90 KB. Isso
é proposital: a capa que está hoje no perfil da Lar Viva tem 202 KB em base64 e
sozinha responde por metade do peso da tela de perfil.
"""
import argparse
import base64
import io
import pathlib
import re
import sys
import unicodedata
from datetime import datetime

import pymysql

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from seed_demo import conectar, senha_do_backend  # noqa: E402,F401

LARGURAS = {"capa": 1200, "local": 900, "prestacao": 1000}
QUALIDADE = 80


def carregar_pillow():
    try:
        from PIL import Image
        return Image
    except ImportError:
        sys.exit("Falta a biblioteca de imagem. Rode:  pip install pillow")


def reduzir(caminho, largura_max, Image):
    """Reduz e converte para JPEG; devolve o base64 pronto para o banco."""
    img = Image.open(caminho)
    if img.mode in ("RGBA", "P", "LA"):
        fundo = Image.new("RGB", img.size, (255, 255, 255))
        fundo.paste(img, mask=img.split()[-1] if img.mode in ("RGBA", "LA") else None)
        img = fundo
    else:
        img = img.convert("RGB")
    if img.width > largura_max:
        altura = int(img.height * largura_max / img.width)
        img = img.resize((largura_max, altura), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=QUALIDADE, optimize=True)
    dados = buf.getvalue()
    return "data:image/jpeg;base64," + base64.b64encode(dados).decode(), len(dados)


def sem_acento(s):
    return "".join(c for c in unicodedata.normalize("NFD", s) if unicodedata.category(c) != "Mn")


def achar_ong(cur, chave):
    """Aceita o id (33) ou o nome em formato de arquivo (lar-viva)."""
    if chave.isdigit():
        cur.execute("SELECT id, nome FROM ong WHERE id = %s", (int(chave),))
        return cur.fetchone()
    alvo = re.sub(r"[^a-z0-9]+", "", sem_acento(chave).lower())
    cur.execute("SELECT id, nome FROM ong WHERE data_exclusao IS NULL")
    for oid, nome in cur.fetchall():
        if re.sub(r"[^a-z0-9]+", "", sem_acento(nome).lower()) == alvo:
            return (oid, nome)
    return None


def main():
    p = argparse.ArgumentParser(description="Sobe fotos reais para o banco do Connect ONG")
    p.add_argument("--pasta", required=True, help="pasta com as subpastas capa/, local/ e prestacao/")
    p.add_argument("--host", default="143.106.241.3")
    p.add_argument("--porta", type=int, default=3306)
    p.add_argument("--usuario", default="cl203161")
    p.add_argument("--banco", default="cl203161")
    p.add_argument("--senha", default=None)
    p.add_argument("--listar", action="store_true", help="mostra o que faria, sem gravar")
    a = p.parse_args()

    Image = carregar_pillow()
    raiz = pathlib.Path(a.pasta)
    if not raiz.exists():
        sys.exit(f"Pasta nao encontrada: {raiz}")

    con = conectar(a)
    cur = con.cursor()
    total, bytes_totais = 0, 0

    # ---- capas do perfil ----
    for arq in sorted((raiz / "capa").glob("*")):
        if arq.suffix.lower() not in (".jpg", ".jpeg", ".png", ".webp"):
            continue
        ong = achar_ong(cur, arq.stem)
        if not ong:
            print(f"  ? nao achei ONG para {arq.name}")
            continue
        b64, tam = reduzir(arq, LARGURAS["capa"], Image)
        print(f"  capa   {ong[1][:38]:40} {tam/1024:6.0f} KB  <- {arq.name}")
        if not a.listar:
            cur.execute("UPDATE ong SET capa_base64 = %s WHERE id = %s", (b64, ong[0]))
        total += 1
        bytes_totais += tam

    # ---- fotos do local (máximo 5 por ONG, é o que o app mostra) ----
    porong = {}
    for arq in sorted((raiz / "local").glob("*")):
        if arq.suffix.lower() not in (".jpg", ".jpeg", ".png", ".webp"):
            continue
        chave = arq.stem.split("-")[0]
        ong = achar_ong(cur, chave)
        if not ong:
            print(f"  ? nao achei ONG para {arq.name}")
            continue
        porong.setdefault(ong, []).append(arq)
    for ong, arquivos in porong.items():
        if not a.listar:
            cur.execute("DELETE FROM ong_foto WHERE ong_id = %s", (ong[0],))
            cur.execute("SELECT COALESCE(MAX(id), 0) FROM ong_foto")
            prox = cur.fetchone()[0] + 1
        for i, arq in enumerate(arquivos[:5]):
            b64, tam = reduzir(arq, LARGURAS["local"], Image)
            print(f"  local  {ong[1][:38]:40} {tam/1024:6.0f} KB  <- {arq.name}")
            if not a.listar:
                cur.execute("INSERT INTO ong_foto (id, ong_id, foto, criado_em) VALUES (%s,%s,%s,%s)",
                            (prox + i, ong[0], b64, datetime.now()))
            total += 1
            bytes_totais += tam

    # ---- fotos das prestações de contas ----
    for arq in sorted((raiz / "prestacao").glob("*")):
        if arq.suffix.lower() not in (".jpg", ".jpeg", ".png", ".webp"):
            continue
        if not arq.stem.split("-")[0].isdigit():
            print(f"  ? o nome do arquivo da prestacao precisa comecar pelo id: {arq.name}")
            continue
        pid = int(arq.stem.split("-")[0])
        cur.execute("SELECT id, titulo FROM prestacao WHERE id = %s", (pid,))
        pr = cur.fetchone()
        if not pr:
            print(f"  ? nao existe prestacao de id {pid} ({arq.name})")
            continue
        b64, tam = reduzir(arq, LARGURAS["prestacao"], Image)
        print(f"  presta {pr[1][:38]:40} {tam/1024:6.0f} KB  <- {arq.name}")
        if not a.listar:
            cur.execute("SELECT COALESCE(MAX(id), 0) FROM prestacao_foto")
            prox = cur.fetchone()[0] + 1
            cur.execute("INSERT INTO prestacao_foto (id, prestacao_id, foto, criado_em) "
                        "VALUES (%s,%s,%s,%s)", (prox, pid, b64, datetime.now()))
        total += 1
        bytes_totais += tam

    if a.listar:
        con.rollback()
        print(f"\n(--listar) {total} imagens seriam gravadas, {bytes_totais/1024:.0f} KB no total.")
    else:
        con.commit()
        print(f"\nPronto: {total} imagens gravadas, {bytes_totais/1024:.0f} KB no total.")
    con.close()


if __name__ == "__main__":
    main()
