# Como colocar fotos reais nas ONGs e nas prestações de contas

O texto do banco eu escrevo; a foto não — eu não gero imagem. Mas deixei o
trabalho braçal pronto: você só escolhe as imagens, coloca numa pasta com o
nome certo e roda um comando. O script reduz, converte e grava no banco no
formato que o aplicativo usa.

---

## Passo 1 — instalar a biblioteca de imagem (uma vez só)

```bash
pip install pillow
```

## Passo 2 — baixar as fotos

Sites com fotos de uso livre, sem problema de direito autoral (não precisa dar
crédito, pode usar em trabalho escolar e apresentação):

- **https://www.pexels.com**
- **https://unsplash.com**
- **https://pixabay.com**

Busque **em inglês**, que devolve muito mais resultado:

| Tipo de ONG | O que buscar |
|---|---|
| Idosos | `elderly care home`, `nursing home volunteer` |
| Crianças | `children classroom donation`, `kids after school` |
| Animais | `animal shelter dog`, `cat shelter volunteer` |
| Alimentos | `food bank volunteers`, `food donation boxes` |
| Situação de rua | `homeless shelter volunteer`, `blanket donation` |
| Educação | `community library`, `students studying` |
| Prestação de contas | `donation boxes stacked`, `volunteers unloading` |

Dica: prefira fotos **horizontais** para a capa (elas aparecem deitadas no topo
do perfil) e evite fotos com rosto em primeiro plano — além de ficarem melhores
como capa, evitam qualquer discussão sobre imagem de pessoas.

## Passo 3 — organizar a pasta

Crie uma pasta (pode ser na sua área de trabalho) com esta estrutura. **O nome
do arquivo diz onde a foto vai:**

```
fotos/
  capa/
    lar-viva.jpg              <- capa do perfil, achando a ONG pelo nome
    33.jpg                    <- ou pelo id da ONG, tanto faz
    instituto-crianca-feliz.jpg
    abrigo-patinhas.jpg
    casa-renascer.jpg
  local/
    lar-viva-1.jpg            <- fotos do local (até 5 por ONG)
    lar-viva-2.jpg
    abrigo-patinhas-1.jpg
  prestacao/
    128.jpg                   <- foto da prestação de contas de id 128
```

O nome pela ONG aceita acento e espaço à vontade: `Lar Viva.jpg`,
`lar-viva.jpg` e `larviva.jpg` acham a mesma instituição.

## Passo 4 — conferir antes de gravar

```bash
cd "C:\Users\01gabriel.MAQCHINELATTO\IdeaProjects\connect-ong-api"
python ferramentas/subir_fotos.py --pasta "C:\caminho\da\pasta\fotos" --listar
```

Ele mostra o que faria (qual foto vai para qual ONG e com quantos KB) **sem
gravar nada**. Se algum nome não bater com nenhuma ONG, ele avisa.

## Passo 5 — gravar

```bash
python ferramentas/subir_fotos.py --pasta "C:\caminho\da\pasta\fotos"
```

Pronto: abra o perfil da ONG no aplicativo e a foto já está lá. Não precisa
reiniciar o servidor.

---

## Por que o script reduz as fotos

A capa que está hoje no perfil da Lar Viva ocupa **202 KB** e a foto do local
outros **80 KB**. Só essas duas imagens são **88% do peso** da tela de perfil
(321 KB no total) — ou seja, quase toda a espera para abrir uma ONG é a foto
viajando pela internet, não o sistema.

O script entrega capa com no máximo 1200px de largura e qualidade 80, o que dá
por volta de **60 a 90 KB** por imagem: some a espera, e na tela ninguém
distingue a diferença.

## Quais ONGs vale a pena ilustrar

Não precisa fotografar 2.000 ONGs. Nove imagens já cobrem toda a apresentação:

1. **Lar Viva** — capa + 2 fotos do local *(é a ONG que abre no telão)*
2. **Instituto Criança Feliz** — capa
3. **Abrigo Patinhas** — capa
4. **Casa Renascer** — capa
5. **Três prestações de contas da Lar Viva** — para a aba de prestações mostrar
   foto do que foi feito com a doação

Para descobrir os ids das prestações da Lar Viva:

```bash
python -c "import sys; sys.path.insert(0,'ferramentas'); from seed_demo import conectar; import argparse; a=argparse.Namespace(host='143.106.241.3',porta=3306,usuario='cl203161',banco='cl203161',senha=None); c=conectar(a); cur=c.cursor(); cur.execute(\"SELECT p.id, p.titulo FROM prestacao p JOIN interesse i ON i.id=p.interesse_id JOIN necessidade n ON n.id=i.necessidade_id WHERE n.ong_id=33\"); [print(x) for x in cur.fetchall()]"
```

---

## Se preferir fazer pelo aplicativo

Dá para fazer tudo pela tela, sem comando nenhum: entre no painel da ONG com
`demo.larviva@connectong.com` / `demo123`, vá em **Editar perfil** e use os
botões de capa e de fotos do local. Só cuide do tamanho: o aplicativo aceita
imagens grandes, e uma foto de 3 MB tirada do celular deixa o perfil pesado
para sempre. Reduza antes (qualquer site de "comprimir imagem" serve) ou use o
script, que já faz isso sozinho.

---

## E se eu quiser ilustrar TODAS as ONGs de uma vez?

O `subir_fotos.py` acima é para escolher a dedo: você separa a foto e diz de
qual ONG ela é. Isso resolve as 6 instituições que aparecem no telão, mas deixa
as outras 1.994 com o cabeçalho vazio — e o visitante da feira que abre uma ONG
qualquer vê a diferença na hora.

Para cobrir o banco inteiro existe o **`ilustrar_demo.py`**:

```bash
python ferramentas/ilustrar_demo.py \
  --imagens "...\FEIRA ESCOLA\interno\imagens-demo" \
  --host 127.0.0.1 --usuario feira --senha feira123 \
  --sql "...\FEIRA ESCOLA\interno\fotos-demo.sql"
```

Ele descobre a **causa** de cada ONG pelo nome (toda ONG gerada carrega um
"núcleo" da sua causa: *Lar Viva*, *Abrigo Patinhas*, *Semente do Amanhã*…) e
dá a ela:

- um **logo** — disco na cor da causa com um pictograma branco (desenho nosso,
  feito com a fonte Material Icons; não é marca de ninguém);
- uma **capa** — foto de licença livre daquela causa;
- e, de quebra, um **retrato** para cada doador, escolhido pelo sexo do primeiro
  nome, para a foto combinar com o nome que aparece ao lado.

As 6 ONGs do telão continuam com a capa escolhida a dedo: elas estão em
`imagens-demo/capa-curada/<id>.jpg` e o script nunca as substitui.

### Por que ele também gera um `.sql`

O `RESTAURAR-DEMO.bat` (o "voltar ao início" entre uma apresentação e outra)
reimporta o dump da escola — que **não tem imagem nenhuma**. Sem uma forma
rápida de repor, as fotos sumiriam na primeira restauração.

O `.sql` gerado agrupa as ONGs que dividem a mesma imagem num único
`UPDATE ... WHERE id IN (...)`: são ~290 comandos no lugar de 5.200, o arquivo
fica em ~5 MB (em vez de ~150 MB) e a reposição leva ~35 s. O `RESTAURAR-DEMO`
e o `ATUALIZAR-BANCO-DA-ESCOLA` já rodam esse arquivo sozinhos.

De onde vem cada imagem, com autor e licença: `imagens-demo/CREDITOS.md`.
Os scripts que geram as imagens estão em `ferramentas/imagens/`.
