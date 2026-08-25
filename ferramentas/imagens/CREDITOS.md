# Créditos das imagens da demonstração

Este arquivo existe para deixar claro **de onde veio cada imagem** que o
Connect ONG usa na demonstração da feira. Nada aqui é foto de ONG real:
as 2.000 instituições do banco são fictícias, geradas para a apresentação.

## O que é cada coisa

| Imagem | Origem | Situação de direito |
|---|---|---|
| **Logo das ONGs** (44 desenhos) | Feitos por nós: disco chapado na cor da causa + pictograma da família **Material Icons** (Google, licença Apache 2.0, já distribuída junto com o Flutter). | Sem restrição. Não imita marca de ninguém. |
| **Capas dos perfis** (54 fotos) | Fotos de licença livre (Creative Commons / domínio público) do **Wikimedia Commons** e do **Openverse**. A lista completa, com autor, licença e link, está no fim deste arquivo. | Uso livre, inclusive comercial, para as licenças CC BY / CC BY-SA / CC0. |
| **Fotos dos doadores** (198 retratos) | **randomuser.me**, banco de retratos usado há anos em protótipos e demonstrações. | Uso **não comercial**. É o caso: trabalho escolar (FECITEC), roda só no notebook da feira, nada é publicado. |

As capas ficam **só no banco local da feira**. O site publicado e o banco da
escola continuam sem elas, por decisão do projeto.

## Como refazer

```bash
# 1) as imagens (no repositório do backend, pasta ferramentas/imagens)
python buscar_openverse.py <causa>     # candidatas + folha de contato
python montar_capas.py                 # recorta 16:9, comprime, escolhe
python gerar_logos.py                  # os 44 logos
python baixar_rostos.py                # os 198 retratos

# 2) aplicar no banco e gerar o SQL que o RESTAURAR-DEMO usa
python ferramentas/ilustrar_demo.py --imagens <pasta> \
    --host 127.0.0.1 --usuario feira --senha feira123 \
    --sql "...\FEIRA ESCOLA\interno\fotos-demo.sql"
```

## Lista das capas

### Alimentação

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `alimentacao-0.jpg` | File:Toronto Consulate volunteers at food bank.jpg | US Mission Canada | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=25687516) |
| `alimentacao-1.jpg` | File:Toronto Consulate volunteers at food bank -b.jpg | US Mission Canada | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=25687839) |
| `alimentacao-2.jpg` | 2016 01 09 AMISOM Ethiopian Donation-7 (23644011073) | AMISOM Public Information | CC0 | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=48896300) |

### Animais

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `animais-0.jpg` | Dog in animal shelter in Washington, Iowa | Nhandler | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=2987589) |
| `animais-1.jpg` | File:Rescue dog in Finland.jpg | Havu Pellikka | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=73057895) |
| `animais-2.jpg` | Grover 1143 (5566816316) | Rocky Mountain Feline Rescue (formerly k | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=138685870) |
| `animais-3.jpg` | Brown and white cat- Flickr - Lisa Zins | Lisa Zins | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=93212763) |
| `animais-4.jpg` | Charles (8701136798) | Robert Couse-Baker from California Repub | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=139040232) |

### Crianças e adolescentes

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `criancas-0.jpg` | Rally spotlights after-school programs (8125665617) | Fort Rucker | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=65696282) |
| `criancas-1.jpg` | Children learning in a Zmistovno after-school center supported by Nova | NUSF | CC0 | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=175121999) |
| `criancas-2.jpg` | Randomized control trial of reading program | BigBrotherMouse | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=39963155) |

### Educação

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `educacao-0.jpg` | Children's Reading Corner 1 at Busesa Community Library, Busesa, Bugwe | Vanmulondo | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=156014064) |
| `educacao-1.jpg` | Save the Children READ program in Bangladesh (24540876525) | Beyond Access | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=154862491) |
| `educacao-2.jpg` | Library Books | Josh Felise | CC0 | [stocksnap](https://stocksnap.io/photo/library-books-4TDHSPIMJ6) |
| `educacao-3.jpg` | Save the Children READ program in Bangladesh (22784746815) | Beyond Access | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=154862471) |
| `educacao-4.jpg` | Stacked Books | Matt Bango | CC0 | [stocksnap](https://stocksnap.io/photo/stacked-books-DGF2LFZ6JJ) |
| `educacao-5.jpg` | Save the Children READ program in Bangladesh (24540877235) | Beyond Access | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=154862493) |

### Meio ambiente

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `ambiente-0.jpg` | Tree Planting Event 4 26 14 III (cropped) | Montgomery County Planning Commission | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=110936629) |
| `ambiente-1.jpg` | Beach plastic waste 3 | Fquasie | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=119381607) |
| `ambiente-2.jpg` | File:Morro Bay, CA Sandspit Coastal Cleanup Day (CCD), Saturday, Septe | Mike Baird | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=28291813) |
| `ambiente-3.jpg` | Oregon-beach-cleanup-ocean-blue-project-volunteers | Ocean Blue Project, Inc. | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=131443370) |
| `ambiente-4.jpg` | Parkdale Community garden volunteer day | Oceanflynn | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=44876949) |
| `ambiente-5.jpg` | Indigenous Garden Volunteering | Jvlara | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=159120299) |

### Moradia e situação de rua

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `moradia-0.jpg` | Habitat for Humanity at Fremont Fair 2007 - 05 | Joe Mabel | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=2263207) |
| `moradia-1.jpg` | Davidson Penland House, Galveston | Jim Evans | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=82473547) |
| `moradia-2.jpg` | Sleeping rough on Christmas Day morning - two homeless people in Woolw | Alisdare Hickson from Woolwich, United K | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=105548274) |

### Mulheres

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `mulheres-0.jpg` | Wiki Loves Women, Focus Group, SheSaid Sudan ... journalists - مشروع ا | Hassan Hassoon | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=116398949) |
| `mulheres-1.jpg` | Wiki Loves Women, Focus Group, SheSaid Sudan ... journalists - مشروع ا | Hassan Hassoon | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=116398954) |
| `mulheres-2.jpg` | A Group of Women Meets at a Health Post to Discuss Issues of Common Co | USAID Ethiopia from Addis Ababa, Ethiopi | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=70817928) |
| `mulheres-3.jpg` | New Destiny Tailoring Project | configmanager | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=147836091) |

### Pessoas com deficiência

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `deficiencia-0.jpg` | Australian paralympian Liesl Tesch conducted wheelchair basketball cli | Department of Foreign Affairs and Trade | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=32168021) |
| `deficiencia-1.jpg` | File:Germany vs Japan women's wheelchair basketball team at the Sports | Bidgee | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=20585082) |
| `deficiencia-2.jpg` | Inclusive education supported by the Philippines-Australia Basic Educa | Department of Foreign Affairs and Trade | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=109250252) |
| `deficiencia-3.jpg` | Sign language interpreter with Wiki4Inclusion facilitator | Kalist Charles Mapambano | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=151552373) |
| `deficiencia-4.jpg` | Australia - Canada, women's wheelchair basketball at Paralympics 2012 | cdephotos | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=21143338) |

### Pessoas idosas

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `idosos-0.jpg` | Maria Flake Boswell Home for Old Women | Jim Evans | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=91950530) |
| `idosos-1.jpg` | File:Alters- und Pflegeheim Wangensbach in Küsnacht ZH.jpg | <a href="//commons.wikimedia.org/w/index | CC0 | [wikimedia commons](https://commons.wikimedia.org/wiki/File:Alters-_und_Pflegeheim_Wangensbach_in_K%C3%BCsnacht_ZH.jpg) |
| `idosos-2.jpg` | File:Asilo José María de Yermo y Parres, Puebla.jpg | <a href="//commons.wikimedia.org/w/index | CC BY-SA 3.0 | [wikimedia commons](https://commons.wikimedia.org/wiki/File:Asilo_Jos%C3%A9_Mar%C3%ADa_de_Yermo_y_Parres,_Puebla.jpg) |
| `idosos-3.jpg` | File:Bonnington Bank House, 205 Ferry Road, Edinburgh.jpg | <a href="//commons.wikimedia.org/wiki/Us | CC BY-SA 4.0 | [wikimedia commons](https://commons.wikimedia.org/wiki/File:Bonnington_Bank_House,_205_Ferry_Road,_Edinburgh.jpg) |
| `idosos-4.jpg` | File:Cooper's Hill House, Englefield Green.jpg | <a href="//commons.wikimedia.org/w/index | CC BY-SA 4.0 | [wikimedia commons](https://commons.wikimedia.org/wiki/File:Cooper%27s_Hill_House,_Englefield_Green.jpg) |

### Saúde

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `saude-0.jpg` | Flickr - DFID - A female doctor with the International Medical Corps e | DFID - UK Department for International D | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=14873860) |
| `saude-1.jpg` | Clinic Front | Twobroxs | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=48419462) |
| `saude-2.jpg` | Mass-Community Health Teaching | Lindseymaya | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=20163728) |
| `saude-3.jpg` | Pehunco Health Clinic opens (10601895075) | U.S. Army Corps of Engineers from USA | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=29551294) |

### Trabalho e renda

| Arquivo | Título | Autor | Licença | Origem |
|---|---|---|---|---|
| `trabalho-0.jpg` | Võrumaa Kutsehariduskeskus 006-metallikoda | Lauri Veerde | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=95455931) |
| `trabalho-1.jpg` | Võrumaa Kutsehariduskeskus 008-puidutookoda | Lauri Veerde | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=95455933) |
| `trabalho-2.jpg` | Workshop at Regional Vocational Training Centre in Knjaževac 01 | Neboysha87 | BY-SA | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=87502525) |
| `trabalho-3.jpg` | Thimphu, traditional arts, sewing class (15656544270) | Arian Zwegers from Brussels, Belgium | BY | [wikimedia](https://commons.wikimedia.org/w/index.php?curid=61923020) |
