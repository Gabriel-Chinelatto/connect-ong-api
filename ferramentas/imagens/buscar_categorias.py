# -*- coding: utf-8 -*-
"""Candidatas de capa por CATEGORIA do Wikimedia Commons.

Busca por texto no Commons devolve muito acervo fora do tema; a CATEGORIA e
curada por gente e acerta muito mais. Monta uma folha de contato por causa.

Uso:  python buscar_categorias.py <causa>
"""
import io
import json
import os
import sys
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image, ImageDraw

UA = "ConnectONG-Feira/1.0 (trabalho escolar FECITEC; echinelat@gmail.com)"
RAIZ = os.path.dirname(os.path.abspath(__file__))
CAND = os.path.join(RAIZ, "img", "cand2")

CATEGORIAS = {
    "criancas": ["Children playing", "Child care", "Kindergartens",
                 "Children's clubs", "Playgrounds"],
    "idosos": ["Elderly people", "Nursing homes", "Retirement homes",
               "Old age in art", "Senior citizens"],
    "animais": ["Animal shelters", "Animal rescue", "Dogs in shelters",
                "Cat shelters", "Animal welfare organizations"],
    "alimentacao": ["Food banks", "Soup kitchens", "Food distribution",
                    "Charity food", "Community kitchens"],
    "educacao": ["Classrooms", "Public libraries", "Reading rooms",
                 "School libraries", "Adult education"],
    "saude": ["Health clinics", "Community health", "Nurses at work",
              "Medical volunteers", "Vaccination"],
    "moradia": ["Homeless shelters", "Social housing", "Homelessness",
                "Emergency housing", "Habitat for Humanity"],
    "mulheres": ["Women's organizations", "Women at work", "Women's shelters",
                 "Sewing groups", "Women's meetings"],
    "deficiencia": ["Wheelchair users", "Disability sports", "Sign language",
                    "Accessibility", "Inclusive education"],
    "ambiente": ["Tree planting", "Volunteer cleanups", "Community gardens",
                 "Recycling", "Environmental education"],
    "trabalho": ["Vocational education", "Sewing classes", "Workshops (crafts)",
                 "Job training", "Carpentry workshops"],
}


def da_categoria(categoria, quantos=10):
    url = "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode({
        "action": "query", "format": "json",
        "generator": "categorymembers",
        "gcmtitle": f"Category:{categoria}", "gcmtype": "file",
        "gcmlimit": str(quantos), "prop": "imageinfo",
        "iiprop": "url|size|extmetadata", "iiurlwidth": "1000",
    })
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        dados = json.load(r)
    saida = []
    for pag in (dados.get("query", {}).get("pages", {}) or {}).values():
        ii = (pag.get("imageinfo") or [{}])[0]
        if not ii.get("thumburl"):
            continue
        larg, alt = ii.get("width") or 0, ii.get("height") or 1
        if larg < 900 or larg / alt < 1.15:       # so horizontais razoaveis
            continue
        meta = ii.get("extmetadata") or {}
        saida.append({
            "titulo": pag.get("title", ""), "url": ii["thumburl"],
            "pagina": ii.get("descriptionurl", ""), "categoria": categoria,
            "licenca": (meta.get("LicenseShortName", {}) or {}).get("value", "?"),
            "autor": (meta.get("Artist", {}) or {}).get("value", "")[:120],
        })
    return saida


def baixar(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def main():
    causa = sys.argv[1]
    os.makedirs(os.path.join(CAND, causa), exist_ok=True)
    achados, vistos = [], set()
    for cat in CATEGORIAS[causa]:
        try:
            for c in da_categoria(cat):
                if c["titulo"] in vistos:
                    continue
                vistos.add(c["titulo"])
                achados.append(c)
        except Exception as e:
            print(f"  x categoria '{cat}': {e}")
    achados = achados[:30]

    # O upload.wikimedia.org derruba rajada: 3 de cada vez, com uma repetida.
    import time

    def uma(par):
        i, c = par
        for tentativa in range(3):
            try:
                return i, Image.open(io.BytesIO(baixar(c["url"]))).convert("RGB")
            except Exception as e:
                erro = e
                time.sleep(1.5 * (tentativa + 1))
        print(f"  x {i:02d}: {erro}")
        return i, None

    with ThreadPoolExecutor(max_workers=3) as ex:
        baixadas = {i: im for i, im in ex.map(uma, list(enumerate(achados))) if im}

    cel_w, cel_h, cols = 300, 210, 6
    linhas = max(1, (len(achados) + cols - 1) // cols)
    folha = Image.new("RGB", (cel_w * cols, cel_h * linhas), (24, 24, 28))
    d = ImageDraw.Draw(folha)
    for i, c in enumerate(achados):
        img = baixadas.get(i)
        if img is None:
            continue
        img.save(os.path.join(CAND, causa, f"{i:02d}.jpg"), "JPEG", quality=88)
        mini = img.copy()
        mini.thumbnail((cel_w - 8, cel_h - 26))
        d.rectangle([(i % cols) * cel_w + 2, (i // cols) * cel_h + 2,
                     (i % cols) * cel_w + 34, (i // cols) * cel_h + 18], fill=(220, 40, 40))
        folha.paste(mini, ((i % cols) * cel_w + (cel_w - mini.width) // 2,
                           (i // cols) * cel_h + 20 + (cel_h - 26 - mini.height) // 2))
        d.text(((i % cols) * cel_w + 8, (i // cols) * cel_h + 5), f"{i:02d}", fill=(255, 255, 255))

    folha.save(os.path.join(RAIZ, "img", f"cat-{causa}.jpg"), "JPEG", quality=82)
    with open(os.path.join(CAND, causa, "meta.json"), "w", encoding="utf-8") as f:
        json.dump(achados, f, ensure_ascii=False, indent=1)
    print(f"cat-{causa}.jpg  ({len(baixadas)}/{len(achados)} imagens)")


if __name__ == "__main__":
    main()
