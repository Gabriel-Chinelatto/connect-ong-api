# -*- coding: utf-8 -*-
"""Gera os LOGOS (foto de perfil) das ONGs da demonstracao.

Um logo = disco chapado na cor da causa + pictograma branco ao centro. Sao
desenhos AUTORAIS (nao ha marca de terceiro), feitos com a familia Material
Icons (Apache 2.0, ja distribuida com o Flutter), entao nao ha questao de
direito de imagem nem de marca.

Saem 4 variantes por causa (cor + pictograma), para ONGs da mesma causa nao
ficarem todas identicas. Sao arquivos pequenos (PNG chapado, ~3 KB).
"""
import os

from PIL import Image, ImageDraw, ImageFont

RAIZ = os.path.dirname(os.path.abspath(__file__))
SAIDA = os.path.join(RAIZ, "img", "logo")
FONTE = r"C:\flutter\bin\cache\artifacts\material_fonts\materialicons-regular.otf"

LADO = 256          # lado do PNG final
DISCO = 0.94        # fracao do lado ocupada pelo disco
GLIFO = 0.50        # fracao do lado ocupada pelo pictograma

# (cor de fundo, codepoint do icone) — 4 combinacoes por causa.
# As cores conversam com o verde da marca (#0A8449) sem repeti-lo em tudo:
# cada causa tem um tom proprio, que e o que faz o logo parecer de uma
# instituicao especifica e nao um enfeite do aplicativo.
VARIANTES = {
    "criancas": [("#F2A03D", "f63c"),   # child_care
                 ("#E4713B", "f5a1"),   # backpack
                 ("#F0B429", "f715"),   # escalator_warning (adulto + crianca)
                 ("#DE6A4E", "f63c")],
    "idosos": [("#7A5CB5", "f6fd"),     # elderly
               ("#8E6BC4", "f0312"),    # elderly_woman
               ("#6D5AA8", "f6fd"),
               ("#9B72C9", "f029b")],   # volunteer_activism
    "animais": [("#3E7EA6", "f0077"),   # pets
                ("#2F6E93", "f0077"),
                ("#4C93B8", "f02f3"),   # cruelty_free
                ("#356B8C", "f0077")],
    "alimentacao": [("#D9534F", "f0108"),   # restaurant
                    ("#C64A46", "f89a"),    # lunch_dining
                    ("#E06B52", "f0108"),
                    ("#B8433F", "f89a")],
    "educacao": [("#2A6FB0", "f8b4"),   # menu_book
                 ("#215D96", "f012e"),  # school
                 ("#3580C4", "f59c"),   # auto_stories
                 ("#1E5288", "f8b4")],
    "saude": [("#159A8C", "f7df"),      # health_and_safety
              ("#0E8578", "f8b0"),      # medical_services
              ("#1BAA9B", "f86f"),      # local_hospital
              ("#0C7568", "f7df")],
    "moradia": [("#8A6A4F", "f7f5"),    # home
                ("#7A5B43", "f0004"),   # night_shelter
                ("#9B7A5C", "f677"),    # cottage
                ("#6E523C", "f7f5")],
    "mulheres": [("#C2557F", "f03bd"),  # woman
                 ("#AD4770", "f0881"),  # diversity_1
                 ("#D0648D", "f029b"),  # volunteer_activism
                 ("#9C3E64", "f03bd")],
    "deficiencia": [("#3F6FD8", "f51e"),   # accessible
                    ("#3560C0", "f081b"),  # sign_language
                    ("#5480E4", "f51d"),   # accessible_forward
                    ("#2E55AC", "f51e")],
    "ambiente": [("#3E8F4E", "f6f2"),   # eco
                 ("#2F7C40", "f0322"),  # forest
                 ("#4EA260", "f0370"),  # recycling
                 ("#276B36", "f004e")], # park
    "trabalho": [("#5B6470", "f02c7"),  # work
                 ("#4C5561", "f7cc"),   # handyman
                 ("#6B7581", "f70f"),   # engineering
                 ("#414A55", "f02c7")],
}


def gerar(cor_hex, codepoint, caminho):
    # Desenha em 4x e reduz: o disco e o glifo saem com a borda lisa.
    escala = 4
    lado = LADO * escala
    img = Image.new("RGB", (lado, lado), (255, 255, 255))
    d = ImageDraw.Draw(img)
    margem = lado * (1 - DISCO) / 2
    d.ellipse([margem, margem, lado - margem, lado - margem], fill=cor_hex)

    fonte = ImageFont.truetype(FONTE, int(lado * GLIFO))
    glifo = chr(int(codepoint, 16))
    caixa = d.textbbox((0, 0), glifo, font=fonte)
    x = (lado - (caixa[2] - caixa[0])) / 2 - caixa[0]
    y = (lado - (caixa[3] - caixa[1])) / 2 - caixa[1]
    d.text((x, y), glifo, font=fonte, fill="white")

    img = img.resize((LADO, LADO), Image.LANCZOS)
    # quantize: PNG chapado de 32 cores fica em ~2-3 KB sem perda visivel.
    img.convert("RGB").quantize(colors=32, method=Image.MEDIANCUT).save(
        caminho, "PNG", optimize=True)
    return os.path.getsize(caminho)


def main():
    os.makedirs(SAIDA, exist_ok=True)
    total = 0
    for causa, variantes in VARIANTES.items():
        for i, (cor, cp) in enumerate(variantes):
            caminho = os.path.join(SAIDA, f"{causa}-{i}.png")
            tam = gerar(cor, cp, caminho)
            total += tam
            print(f"  {causa}-{i}  {cor}  U+{cp.upper()}  {tam/1024:.1f} KB")
    print(f"\n{len(VARIANTES)*4} logos, media {total/len(VARIANTES)/4/1024:.1f} KB")


if __name__ == "__main__":
    main()
