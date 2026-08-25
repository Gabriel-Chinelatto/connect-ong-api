# -*- coding: utf-8 -*-
"""Baixa os retratos usados como foto de perfil dos DOADORES da demonstracao.

Fonte: randomuser.me — banco de retratos de pessoas reais usado ha anos para
prototipo/demonstracao. Uso nao comercial (e o caso: trabalho escolar da
FECITEC, roda so no notebook da feira, nada e publicado).

Saem 99 masculinos + 99 femininos em img/rosto/{m,f}/NN.jpg.
"""
import os
from concurrent.futures import ThreadPoolExecutor

RAIZ = os.path.dirname(os.path.abspath(__file__))
SAIDA = os.path.join(RAIZ, "img", "rosto")
UA = "ConnectONG-Feira/1.0 (trabalho escolar FECITEC)"
QUANTOS = 99


def baixar(par):
    sexo, i = par
    pasta = "men" if sexo == "m" else "women"
    destino = os.path.join(SAIDA, sexo, f"{i:02d}.jpg")
    if os.path.exists(destino) and os.path.getsize(destino) > 1000:
        return True
    import urllib.request
    url = f"https://randomuser.me/api/portraits/{pasta}/{i}.jpg"
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            dados = r.read()
        if len(dados) < 1000:
            return False
        with open(destino, "wb") as f:
            f.write(dados)
        return True
    except Exception:
        return False


def main():
    for s in ("m", "f"):
        os.makedirs(os.path.join(SAIDA, s), exist_ok=True)
    tarefas = [(s, i) for s in ("m", "f") for i in range(QUANTOS)]
    with ThreadPoolExecutor(max_workers=12) as ex:
        ok = sum(1 for r in ex.map(baixar, tarefas) if r)
    print(f"{ok}/{len(tarefas)} retratos baixados em {SAIDA}")


if __name__ == "__main__":
    main()
