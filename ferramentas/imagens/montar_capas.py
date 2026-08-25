# -*- coding: utf-8 -*-
"""Monta o conjunto final de CAPAS por causa, a partir das candidatas escolhidas.

Recorta em 16:9 (formato do cabecalho), reduz e grava em img/capa/<causa>-<n>.jpg
junto com um credito.json (autor/licenca/pagina de origem de cada foto).
"""
import io
import json
import os
import shutil

from PIL import Image

RAIZ = os.path.dirname(os.path.abspath(__file__))
SAIDA = os.path.join(RAIZ, "img", "capa")

# 720px/q62: a capa e um FUNDO (aparece atras de um veu preto de 45% no
# cabecalho e como faixa de 64px no card). Em 900px/q72 dava 68 KB por foto;
# como cada uma das 2.000 ONGs guarda a sua copia no banco, isso viraria ~180 MB
# de base64. Em 720px/q62 cai para ~30 KB sem diferenca visivel na tela.
LARGURA = 720
QUALIDADE = 62
TETO_KB = 38        # foto detalhada perde qualidade ate caber neste teto

# (pasta_das_candidatas, indice) — escolhidas a olho nas folhas de contato.
ESCOLHAS = {
    # Conferidas uma a uma na folha de contato. Ficaram de fora: fotos com placa
    # em ingles bem legivel, retrato de atleta identificavel, sala escura de
    # projetor, repeticoes quase iguais e — principalmente — TUDO que veio do
    # rawpixel, que entrega a imagem com a marca d'agua "rawpixel" ladrilhada
    # por cima (some na miniatura, aparece no cabecalho do perfil).
    "criancas":    [("cand", 0), ("cand", 1), ("cand3", 2)],
    "idosos":      [("cand", 6),
                    ("cand2", 3), ("cand2", 4), ("cand2", 9), ("cand2", 10)],
    "animais":     [("cand", 0), ("cand", 8), ("cand", 14),
                    ("cand3", 12), ("cand3", 14)],
    "alimentacao": [("cand", 1), ("cand3", 6), ("cand3", 9)],
    "educacao":    [("cand", 1), ("cand", 6), ("cand", 18), ("cand", 4),
                    ("cand3", 11), ("cand3", 6)],
    "saude":       [("cand", 13), ("cand", 6), ("cand", 1), ("cand", 5)],
    "moradia":     [("cand", 11), ("cand", 10), ("cand", 1)],
    "mulheres":    [("cand", 0), ("cand", 1), ("cand", 14), ("cand", 17)],
    "deficiencia": [("cand", 4), ("cand", 1), ("cand", 8), ("cand", 11), ("cand", 5)],
    "ambiente":    [("cand3", 2), ("cand3", 8), ("cand3", 10), ("cand3", 11),
                    ("cand3", 16), ("cand3", 21)],
    "trabalho":    [("cand", 0), ("cand", 1), ("cand", 4), ("cand", 8)],
}


def recortar_16x9(img):
    """Corta pelo centro no formato do cabecalho, sem deformar a foto."""
    alvo = 16 / 9
    if img.width / img.height > alvo:
        nova = int(img.height * alvo)
        esq = (img.width - nova) // 2
        img = img.crop((esq, 0, esq + nova, img.height))
    else:
        nova = int(img.width / alvo)
        # corta mais de baixo que de cima: em foto de gente o interesse fica no alto
        topo = int((img.height - nova) * 0.35)
        img = img.crop((0, topo, img.width, topo + nova))
    if img.width > LARGURA:
        img = img.resize((LARGURA, int(LARGURA / alvo)), Image.LANCZOS)
    return img


def main():
    if os.path.exists(SAIDA):
        shutil.rmtree(SAIDA)
    os.makedirs(SAIDA)
    creditos, total = [], 0
    for causa, itens in ESCOLHAS.items():
        for n, (pasta, idx) in enumerate(itens):
            origem = os.path.join(RAIZ, "img", pasta, causa, f"{idx:02d}.jpg")
            if not os.path.exists(origem):
                print(f"  ! FALTA {origem}")
                continue
            img = recortar_16x9(Image.open(origem).convert("RGB"))
            destino = os.path.join(SAIDA, f"{causa}-{n}.jpg")
            # Teto por arquivo: sem isso, uma foto cheia de folhagem/textura
            # sozinha pesa 3x o que pesa uma foto lisa — e sao 2.000 copias no
            # banco. Baixa a qualidade so nas que precisam.
            q = QUALIDADE
            while True:
                img.save(destino, "JPEG", quality=q, optimize=True)
                tam = os.path.getsize(destino)
                if tam <= TETO_KB * 1024 or q <= 46:
                    break
                q -= 6
            total += tam
            print(f"  {causa}-{n}  {img.width}x{img.height}  {tam/1024:5.1f} KB  q{q}")

            meta_arq = os.path.join(RAIZ, "img", pasta, causa, "meta.json")
            try:
                meta = json.load(io.open(meta_arq, encoding="utf-8"))[idx]
            except Exception:
                meta = {}
            creditos.append({
                "arquivo": f"{causa}-{n}.jpg", "causa": causa,
                "titulo": meta.get("titulo"), "autor": meta.get("autor"),
                "licenca": meta.get("licenca"), "pagina": meta.get("pagina"),
                "fonte": meta.get("fonte") or "wikimedia commons",
            })
    with io.open(os.path.join(SAIDA, "creditos.json"), "w", encoding="utf-8") as f:
        json.dump(creditos, f, ensure_ascii=False, indent=1)
    print(f"\n{len(creditos)} capas, media {total/max(1,len(creditos))/1024:.1f} KB")


if __name__ == "__main__":
    main()
