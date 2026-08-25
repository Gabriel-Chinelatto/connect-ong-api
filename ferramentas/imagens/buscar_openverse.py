# -*- coding: utf-8 -*-
"""Busca candidatas de foto no Openverse (CC/licenca livre) e monta uma folha de
contato por causa, para conferencia visual antes de escolher.

Uso:  python buscar_openverse.py <causa>
"""
import io
import json
import os
import sys
import urllib.parse
import urllib.request

from PIL import Image, ImageDraw

UA = "ConnectONG-Feira/1.0 (trabalho escolar FECITEC; echinelat@gmail.com)"
RAIZ = os.path.dirname(os.path.abspath(__file__))
CAND = os.path.join(RAIZ, "img", "cand3")

BUSCAS = {
    "criancas": ["children after school program", "kids classroom activity",
                 "children community center", "kids workshop volunteers"],
    "idosos": ["elderly care home", "senior citizens activity", "elderly volunteer visit",
               "older adults community center"],
    "animais": ["animal shelter dog", "dog rescue shelter", "cat shelter adoption",
                "animal rescue volunteer"],
    "alimentacao": ["food bank volunteers", "food donation boxes", "soup kitchen meal",
                    "community kitchen volunteers"],
    "educacao": ["community library children reading", "classroom students studying",
                 "adult education class", "school library books"],
    "saude": ["community health clinic", "health volunteers checkup",
              "mobile clinic patients", "nurse community outreach"],
    "moradia": ["homeless shelter volunteers", "housing construction volunteers",
                "shelter beds night", "homeless outreach blankets"],
    "mulheres": ["women community workshop", "women support group meeting",
                 "women training sewing", "mothers group community"],
    "deficiencia": ["wheelchair basketball", "disability inclusion classroom",
                    "sign language interpreter", "wheelchair ramp accessibility"],
    "ambiente": ["tree planting volunteers", "beach cleanup volunteers",
                 "community garden volunteers", "recycling sorting center"],
    "trabalho": ["vocational training workshop", "sewing training class",
                 "computer class adults", "carpentry workshop training"],
}


def buscar(termo, quantos=8):
    url = "https://api.openverse.org/v1/images/?" + urllib.parse.urlencode({
        "q": termo, "page_size": str(quantos),
        "license_type": "commercial,modification",
        "aspect_ratio": "wide", "size": "large", "mature": "false",
        # O rawpixel entrega as fotos com MARCA D'AGUA "rawpixel" ladrilhada por
        # cima — bonita na miniatura, vergonhosa no cabecalho do perfil. Fora.
        "excluded_source": "rawpixel",
    })
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        dados = json.load(r)
    return [{
        "titulo": x.get("title") or "", "url": x.get("url"),
        "thumb": x.get("thumbnail") or x.get("url"),
        "pagina": x.get("foreign_landing_url"), "licenca": (x.get("license") or "").upper(),
        "autor": x.get("creator") or "", "fonte": x.get("source") or "",
        "atribuicao": x.get("attribution") or "",
    } for x in dados.get("results", []) if x.get("url")]


def baixar(url, tempo=25):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=tempo) as r:
        return r.read()


def baixar_todas(candidatas):
    """Baixa em paralelo (a fonte original, nao a miniatura do Openverse, que e
    lenta). Devolve {indice: Image}."""
    from concurrent.futures import ThreadPoolExecutor

    import time

    def uma(par):
        i, c = par
        for tentativa in range(3):
            for chave in ("url", "thumb"):
                try:
                    return i, Image.open(io.BytesIO(baixar(c[chave]))).convert("RGB")
                except Exception:
                    pass
            time.sleep(1.5 * (tentativa + 1))
        return i, None

    with ThreadPoolExecutor(max_workers=4) as ex:
        return {i: img for i, img in ex.map(uma, list(enumerate(candidatas))) if img}


def main():
    causa = sys.argv[1]
    os.makedirs(os.path.join(CAND, causa), exist_ok=True)
    achados, vistos = [], set()
    for termo in BUSCAS[causa]:
        try:
            for c in buscar(termo):
                if c["url"] in vistos:
                    continue
                vistos.add(c["url"])
                achados.append(c)
        except Exception as e:
            print(f"  x busca '{termo}': {e}")
    achados = achados[:24]

    cel_w, cel_h, cols = 300, 210, 6
    linhas = max(1, (len(achados) + cols - 1) // cols)
    folha = Image.new("RGB", (cel_w * cols, cel_h * linhas), (24, 24, 28))
    desenho = ImageDraw.Draw(folha)
    ok = 0
    baixadas = baixar_todas(achados)
    for i, c in enumerate(achados):
        img = baixadas.get(i)
        if img is None:
            continue
        img.save(os.path.join(CAND, causa, f"{i:02d}.jpg"), "JPEG", quality=88)
        mini = img.copy()
        mini.thumbnail((cel_w - 8, cel_h - 26))
        x = (i % cols) * cel_w + (cel_w - mini.width) // 2
        y = (i // cols) * cel_h + 20 + (cel_h - 26 - mini.height) // 2
        folha.paste(mini, (x, y))
        desenho.rectangle([(i % cols) * cel_w + 2, (i // cols) * cel_h + 2,
                           (i % cols) * cel_w + 34, (i // cols) * cel_h + 18],
                          fill=(220, 40, 40))
        desenho.text(((i % cols) * cel_w + 8, (i // cols) * cel_h + 5),
                     f"{i:02d}", fill=(255, 255, 255))
        ok += 1

    folha.save(os.path.join(RAIZ, "img", f"folha-{causa}.jpg"), "JPEG", quality=82)
    with open(os.path.join(CAND, causa, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(achados, f, ensure_ascii=False, indent=1)
    for i, c in enumerate(achados):
        linha = f"{i:02d} [{c['licenca']}] {c['titulo'][:60]} - {c['autor'][:25]}"
        print(linha.encode("ascii", "replace").decode("ascii"))
    print(f"\nfolha: img/folha-{causa}.jpg  ({ok} imagens)")


if __name__ == "__main__":
    main()
