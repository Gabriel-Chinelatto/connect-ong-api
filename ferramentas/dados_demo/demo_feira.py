# -*- coding: utf-8 -*-
"""Conteúdo CURADO das contas usadas na apresentação da feira.

O resto do banco é gerado por combinação (ver conteudo.py). Aqui não: estas
são as contas que vão aparecer no telão, então cada texto foi escrito à mão.

Contas (senha demo123 em todas):
    demo.larviva@connectong.com      -> painel da ONG
    demo.joao@connectong.com         -> app do doador
    demo.ana@connectong.com          -> segunda doadora (aparece nas conversas)
    demo.empresa@connectong.com      -> doador pessoa jurídica

Regra de ouro da demonstração: sempre sobra alguma coisa PARA FAZER AO VIVO.
Por isso a Lar Viva termina com interesses PENDENTES (a ONG aceita na hora) e
com necessidades ABERTAS (o doador demonstra interesse na hora).
"""

# ---------------------------------------------------------------------------
# PERFIS DAS ONGs DE DEMONSTRAÇÃO
# ---------------------------------------------------------------------------
# 'via' é uma rua que existe de verdade na cidade: o seed geocodifica pelo
# Nominatim (o mesmo serviço que o painel usa) e grava a coordenada real.
ONGS_DEMO = {
    "demo.larviva@connectong.com": {
        "nome": "Lar Viva",
        "cidade": "Limeira",
        "uf": "SP",
        "via": "Rua Boa Morte",
        "numero": 745,
        "bairro": "Centro",
        "telefone": "(19) 3441-1000",
        "verificada": 1,
        "descricao": (
            "Fundado em 2004, o Lar Viva acolhe 42 idosos em situação de vulnerabilidade "
            "em Limeira, sendo 15 acamados. A casa funciona 24 horas, com enfermagem em "
            "três turnos, fisioterapia duas vezes por semana e oficina de memória às "
            "quintas. São 28 voluntários e 11 profissionais contratados. Metade do custo "
            "mensal vem de doações: fraldas geriátricas, roupa de cama e alimentos são o "
            "que mais falta. Prestamos contas de tudo o que recebemos pelo aplicativo."
        ),
        "necessidades": [
            ("Fraldas geriátricas tamanho G", "Higiene", "ABERTA", 1,
             "Consumimos cerca de 900 fraldas por mês entre os 15 residentes acamados. É o item que mais pesa no orçamento da casa."),
            ("Cobertores e mantas para o inverno", "Roupas", "ABERTA", 1,
             "São 42 leitos e as noites de junho e julho aqui ficam abaixo de 10 graus. Aceitamos cobertores usados em bom estado."),
            ("Suplemento alimentar e alimentos pastosos", "Alimentos", "ABERTA", 0,
             "Nove residentes têm dificuldade de mastigação e dependem de dieta pastosa e suplemento."),
            ("Lençóis de solteiro e toalhas de banho", "Roupas", "ATENDIDA", 0,
             "A roupa de cama é trocada diariamente. Precisávamos repor o enxoval inteiro da ala norte."),
            ("Luvas e material de curativo", "Saude", "ATENDIDA", 0,
             "Material básico de enfermagem para os curativos diários: luvas, gaze, soro fisiológico e esparadrapo."),
            ("Cadeira de rodas e andadores", "Saude", "ATENDIDA", 0,
             "Equipamentos de mobilidade para uso interno, aceitos usados desde que firmes."),
        ],
        "campanhas": [
            ("Inverno sem frio 2026", "Roupas", 2500, True,
             "Cobertores, mantas e agasalhos para os 42 residentes atravessarem o inverno."),
            ("Fralda todo mês", "Higiene", 3000, True,
             "Manter o estoque de fraldas geriátricas da casa por seis meses."),
            ("Reforma dos banheiros da ala norte", "Saude", 4000, False,
             "Instalar barras de apoio e trocar o piso antiderrapante dos quatro banheiros da ala norte."),
        ],
    },
    "demo.criancafeliz@connectong.com": {
        "nome": "Instituto Criança Feliz",
        "cidade": "Campinas",
        "uf": "SP",
        "via": "Avenida Francisco Glicério",
        "numero": 1220,
        "bairro": "Centro",
        "telefone": "(19) 3232-2000",
        "verificada": 1,
        "descricao": (
            "O Instituto Criança Feliz atende 120 crianças e adolescentes de 6 a 17 anos "
            "no contraturno escolar, no Centro de Campinas, desde 2011. Oferece reforço "
            "de português e matemática, oficina de música, aula de informática e lanche "
            "todos os dias úteis. A equipe reúne 19 voluntários e 8 profissionais. "
            "Material escolar, leite e brinquedos pedagógicos são as doações que "
            "sustentam a rotina da casa."
        ),
        "necessidades": [
            ("Material escolar para o ano letivo", "Educacao", "ABERTA", 1,
             "Cadernos, lápis, canetas e mochilas para as 120 crianças atendidas. Os kits são entregues na primeira semana de aula."),
            ("Leite em pó para o lanche da tarde", "Alimentos", "ABERTA", 0,
             "Servimos lanche todos os dias úteis. Precisamos de leite em pó integral, latas de 400g."),
            ("Brinquedos pedagógicos", "Brinquedos", "ATENDIDA", 0,
             "Jogos de encaixe, quebra-cabeças e brinquedos de montar para a sala de atividades."),
            ("Livros infantis e juvenis", "Educacao", "ATENDIDA", 0,
             "Estamos montando a biblioteca da casa. Aceitamos livros usados em bom estado."),
        ],
        "campanhas": [
            ("Volta às aulas 2026", "Educacao", 3000, True,
             "Compra de 120 kits de material escolar completos para o início do ano letivo."),
            ("Lanche todo dia", "Alimentos", 2000, False,
             "Garantir o lanche da tarde por seis meses para as turmas do contraturno."),
        ],
    },
    "demo.patinhas@connectong.com": {
        "nome": "Abrigo Patinhas",
        "cidade": "Piracicaba",
        "uf": "SP",
        "via": "Avenida Independência",
        "numero": 2870,
        "bairro": "Alemães",
        "telefone": "(19) 3422-3000",
        "verificada": 1,
        "descricao": (
            "O Abrigo Patinhas cuida de 86 cães e 40 gatos resgatados das ruas de "
            "Piracicaba desde 2015. Mantém feira de adoção aos sábados, castração "
            "mensal a baixo custo e uma rede de lares temporários com 22 voluntários. "
            "Todo animal resgatado passa por vermifugação, vacinação e castração antes "
            "de ir para adoção. Ração e medicamento veterinário consomem quase todo o "
            "orçamento: são cerca de 380 kg de ração por mês."
        ),
        "necessidades": [
            ("Ração para cães adultos", "Alimentos", "ABERTA", 1,
             "Abrigamos 86 cães e consumimos cerca de 380 kg de ração por mês. Aceitamos qualquer marca, inclusive pacote aberto."),
            ("Vermífugo e antipulgas", "Saude", "ABERTA", 0,
             "Todo animal resgatado passa por vermifugação e controle de pulgas antes de ir para adoção."),
            ("Cobertores e caminhas", "Higiene", "ATENDIDA", 0,
             "No frio os canis precisam de forração. Aceitamos cobertores usados e toalhas velhas."),
        ],
        "campanhas": [
            ("Castração solidária", "Saude", 2500, True,
             "Castrar 60 animais resgatados e de tutores de baixa renda do bairro."),
            ("Ração o ano todo", "Alimentos", 5000, False,
             "Garantir a ração dos 126 animais abrigados pelos próximos seis meses."),
        ],
    },
    "demo.renascer@connectong.com": {
        "nome": "Casa Renascer",
        "cidade": "Limeira",
        "uf": "SP",
        "via": "Rua Santa Cruz",
        "numero": 388,
        "bairro": "Vila Cláudia",
        "telefone": "(19) 3441-4000",
        "verificada": 0,
        "descricao": (
            "A Casa Renascer atende pessoas em situação de rua e em processo de "
            "reinserção social em Limeira desde 2019. Oferece banho e lavanderia todos "
            "os dias, 30 vagas de pernoite, encaminhamento para documentação e emprego "
            "e ronda noturna com marmitas três vezes por semana. Roupas adultas, "
            "cobertores e kits de higiene são o que mais sai no atendimento diário."
        ),
        "necessidades": [
            ("Roupas masculinas adultas", "Roupas", "ABERTA", 0,
             "Calças, camisetas e casacos tamanhos M, G e GG. É o que mais sai no atendimento diário."),
            ("Kit de higiene para banho", "Higiene", "ABERTA", 1,
             "Sabonete, xampu, desodorante e barbeador descartável. Oferecemos cerca de 40 banhos por dia."),
            ("Cobertores e sacos de dormir", "Roupas", "ATENDIDA", 0,
             "Distribuímos nas rondas noturnas de inverno. Aceitamos usados em bom estado."),
        ],
        "campanhas": [
            ("Noite sem frio", "Roupas", 1800, True,
             "Cobertores e agasalhos para as rondas noturnas do inverno."),
        ],
    },
}

# ---------------------------------------------------------------------------
# DOADORES DE DEMONSTRAÇÃO
# ---------------------------------------------------------------------------
DOADORES_DEMO = {
    "demo.joao@connectong.com": {
        "nome": "João Pereira",
        "cidade": "Limeira", "uf": "SP",
        "telefone": "(19) 99712-4408",
        "bio": "Doador desde março de 2025. Prefiro entregar pessoalmente e acompanhar a prestação de contas.",
    },
    "demo.ana@connectong.com": {
        "nome": "Ana Costa",
        "cidade": "Campinas", "uf": "SP",
        "telefone": "(19) 99184-2210",
        "bio": "Professora da rede pública. Costumo doar livros e material escolar todo início de ano.",
    },
    "demo.empresa@connectong.com": {
        "nome": "Tech Solutions LTDA",
        "cidade": "Campinas", "uf": "SP",
        "telefone": "(19) 3705-8800",
        "bio": "Empresa de tecnologia. Destinamos parte do faturamento mensal a instituições da região.",
    },
}

# ---------------------------------------------------------------------------
# CONVERSAS CONCLUÍDAS DA CONTA DA FEIRA
# ---------------------------------------------------------------------------
# (ong_email, titulo_necessidade, dias_atras, [(remetente, texto), ...])
# dias_atras = quando o interesse foi criado. A prestação de contas entra
# alguns dias depois da conclusão.
HISTORICO_JOAO = [
    ("demo.larviva@connectong.com", "Lençóis de solteiro e toalhas de banho", 168, [
        ("DOADOR", "Boa tarde! Vi que vocês precisam repor o enxoval da ala norte. Tenho seis jogos de lençol de solteiro novos, comprei em promoção. Ainda serve?"),
        ("ONG", "Boa tarde, João! Serve muito. Estamos trocando roupa de cama todo dia e o enxoval da ala norte já está bem gasto."),
        ("DOADOR", "Ótimo. Consigo levar no sábado de manhã, uns 10h. Vocês estão na Rua Boa Morte, certo?"),
        ("ONG", "Isso mesmo, número 745, Centro. Sábado tem plantão até meio-dia, pode vir tranquilo."),
        ("DOADOR", "Cheguei agora e deixei com a enfermeira do plantão. Levei também quatro toalhas de banho que estavam sobrando aqui."),
        ("ONG", "Recebemos tudo, João. As toalhas vieram em ótima hora, estávamos com o rodízio apertado. Muito obrigada mesmo!"),
        ("DOADOR", "Que bom que ajudou. Qualquer coisa que precisarem, me chamem por aqui."),
        ("ONG", "Pode deixar. Vamos publicar a prestação de contas assim que o enxoval entrar em uso, para você ver onde foi parar."),
    ]),
    ("demo.larviva@connectong.com", "Luvas e material de curativo", 121, [
        ("DOADOR", "Olá! Minha irmã é técnica de enfermagem e conseguiu uma caixa de luvas e gazes com validade longa. Vocês precisam?"),
        ("ONG", "Precisamos sim! Fazemos curativo diário em oito residentes, luva é consumo constante aqui."),
        ("DOADOR", "Perfeito. Posso passar aí na quinta depois do trabalho, umas 18h?"),
        ("ONG", "A recepção fecha às 17h, mas a enfermagem fica 24h. Pode vir às 18h que eu deixo avisado no plantão."),
        ("DOADOR", "Entreguei agora. Falei com a Dona Marta, ela conferiu tudo."),
        ("ONG", "Confirmado, ela já registrou no estoque. Isso cobre praticamente o mês inteiro de curativos. Obrigada!"),
    ]),
    ("demo.criancafeliz@connectong.com", "Brinquedos pedagógicos", 96, [
        ("DOADOR", "Boa tarde! Meus sobrinhos cresceram e sobrou uma caixa de jogos de encaixe e dois quebra-cabeças, todos completos. Interessa?"),
        ("ONG", "Boa tarde, João! Interessa muito. A sala de atividades atende 120 crianças e os jogos acabam se desgastando rápido."),
        ("DOADOR", "Estão bem conservados, conferi peça por peça. Como faço para entregar?"),
        ("ONG", "Se puder trazer até a Avenida Francisco Glicério, 1220, ótimo. De segunda a sexta até as 17h."),
        ("DOADOR", "Levei hoje de manhã. Aproveitei e conheci a sala de informática, muito bem organizada."),
        ("ONG", "Obrigada pela visita! As crianças da turma da tarde já estavam usando os jogos no mesmo dia."),
    ]),
    ("demo.patinhas@connectong.com", "Cobertores e caminhas", 74, [
        ("DOADOR", "Oi! Fiz uma limpeza no armário e separei cobertores velhos, mas limpos. Servem para forrar os canis?"),
        ("ONG", "Servem perfeitamente! É exatamente o que usamos na forração no inverno. Não precisa estar novo, só limpo e sem cheiro forte."),
        ("DOADOR", "Tenho uns oito. Piracicaba é um pouco longe para mim, dá para combinar retirada?"),
        ("ONG", "Dá sim. Nosso voluntário faz rota por Limeira às terças. Pode ser terça de manhã?"),
        ("DOADOR", "Pode. Ficarei em casa até as 11h."),
        ("ONG", "Ele passou e pegou tudo, obrigado! Já forramos os canis do setor dos idosos, que são os que mais sentem frio."),
    ]),
    ("demo.renascer@connectong.com", "Cobertores e sacos de dormir", 58, [
        ("DOADOR", "Boa noite. Vi a campanha de inverno de vocês. Tenho quatro cobertores de casal para doar."),
        ("ONG", "Boa noite, João! Chegou na hora certa, a ronda desta semana está com pouca coisa para distribuir."),
        ("DOADOR", "Consigo deixar amanhã cedo antes do trabalho, umas 7h30. Alguém estará aí?"),
        ("ONG", "Sim, a casa não fecha. Pode entregar no portão da Rua Santa Cruz, 388."),
        ("DOADOR", "Deixei com o rapaz do plantão. Espero que ajude alguém hoje à noite."),
        ("ONG", "Ajudou quatro pessoas na ronda de ontem. Um deles dormia embaixo do viaduto há semanas. Obrigado de verdade."),
    ]),
    ("demo.criancafeliz@connectong.com", "Livros infantis e juvenis", 41, [
        ("DOADOR", "Olá! Vocês ainda estão montando a biblioteca? Tenho uns 20 livros infantis em bom estado."),
        ("ONG", "Estamos sim! A biblioteca é nova e a estante ainda tem espaço sobrando."),
        ("DOADOR", "São livros de literatura infantil e alguns juvenis. Levo na sexta."),
        ("ONG", "Combinado. Se puder chegar antes das 16h, as crianças estão em atividade e você vê a roda de leitura acontecendo."),
        ("DOADOR", "Cheguei às 15h e assisti um pouco. Vale muito a pena ver de perto."),
        ("ONG", "Ficamos felizes com a visita! Os livros já foram catalogados e entraram na roda desta semana."),
    ]),
    ("demo.larviva@connectong.com", "Cadeira de rodas e andadores", 27, [
        ("DOADOR", "Bom dia! Meu avô faleceu e ficou uma cadeira de rodas dobrável e um andador, ambos em bom estado. Vocês aceitam?"),
        ("ONG", "Bom dia, João. Aceitamos sim, e sinto muito pela sua perda. Equipamento de mobilidade é sempre necessário aqui."),
        ("DOADOR", "Obrigado. Prefiro que seja usado por alguém do que ficar parado em casa."),
        ("ONG", "É exatamente assim que a gente pensa. Pode trazer quando for melhor para você."),
        ("DOADOR", "Levei hoje à tarde. A cadeira precisa de uma regulagem no freio, mas está inteira."),
        ("ONG", "Nosso fisioterapeuta já ajustou o freio. A cadeira ficou com a Dona Nair, que estava usando uma emprestada. Obrigada, João."),
    ]),
]

# Conversas ainda EM ANDAMENTO (status ACEITO) - a demonstração começa com o
# chat vivo, não com tudo terminado.
EM_ANDAMENTO_JOAO = [
    ("demo.larviva@connectong.com", "Suplemento alimentar e alimentos pastosos", 6, [
        ("DOADOR", "Boa tarde! Consegui seis latas de suplemento alimentar com meu vizinho, que é nutricionista. Ainda precisam?"),
        ("ONG", "Boa tarde, João! Precisamos muito. Nove residentes dependem de dieta pastosa e o suplemento é caro."),
        ("DOADOR", "Ótimo. Estou terminando uma viagem, entrego no fim de semana. Pode ser?"),
        ("ONG", "Pode sim, ficamos no aguardo. Obrigada por lembrar da gente!"),
    ]),
    ("demo.patinhas@connectong.com", "Ração para cães adultos", 2, [
        ("DOADOR", "Oi! Comprei um saco de 15 kg de ração para doar. Vocês retiram em Limeira?"),
        ("ONG", "Oi, João! Retiramos sim, o voluntário passa por aí na terça. Pode deixar separado?"),
    ]),
]

# Doadores que conversaram com a Lar Viva (para o PAINEL DA ONG ter movimento)
HISTORICO_LARVIVA = [
    ("demo.ana@connectong.com", "Fraldas geriátricas tamanho G", 133, "CONCLUIDO", [
        ("DOADOR", "Olá! Trabalho numa farmácia e conseguimos separar 4 pacotes de fralda geriátrica G. Posso doar?"),
        ("ONG", "Olá, Ana! Claro que pode. Fralda é o item que mais falta aqui, obrigada!"),
        ("DOADOR", "Levo na quarta de manhã então."),
        ("ONG", "Perfeito, estaremos aqui. Muito obrigada pela lembrança."),
        ("DOADOR", "Entregue! Se sobrar de novo eu aviso."),
        ("ONG", "Recebemos, Ana. Isso cobre quase cinco dias de consumo da casa. Gratidão!"),
    ]),
    ("demo.empresa@connectong.com", "Cobertores e mantas para o inverno", 89, "CONCLUIDO", [
        ("DOADOR", "Boa tarde. Somos uma empresa de Campinas e fizemos uma campanha interna: arrecadamos 30 cobertores novos."),
        ("ONG", "Boa tarde! Trinta cobertores novos cobrem quase toda a casa. Vocês não têm ideia do que isso representa aqui no inverno."),
        ("DOADOR", "Que bom. Conseguimos entregar com nosso veículo na sexta, período da manhã."),
        ("ONG", "Combinado. Vamos deixar o portão da garagem liberado para o descarregamento."),
        ("DOADOR", "Entregamos hoje. Nosso pessoal ficou emocionado com a recepção de vocês."),
        ("ONG", "Nós que agradecemos. Já publicamos a prestação de contas com as fotos dos leitos."),
    ]),
]

# Interesses PENDENTES: ficam por último de propósito, para a ONG aceitar AO
# VIVO durante a apresentação (o painel mostra o botão de aceitar/recusar).
PENDENTES_LARVIVA = [
    ("demo.ana@connectong.com", "Cobertores e mantas para o inverno", 1, [
        ("DOADOR", "Oi! Separei mais cinco cobertores aqui em casa. Vocês ainda estão recebendo?"),
    ]),
    ("demo.empresa@connectong.com", "Fraldas geriátricas tamanho G", 0, [
        ("DOADOR", "Bom dia! Fizemos uma nova arrecadação interna e conseguimos 12 pacotes de fralda G. Podemos entregar esta semana?"),
    ]),
]

# Prestações de contas específicas da Lar Viva (títulos batendo com o que foi doado)
PRESTACOES_LARVIVA = {
    "Lençóis de solteiro e toalhas de banho": (
        "Enxoval da ala norte renovado",
        "Os seis jogos de lençol e as quatro toalhas doadas substituíram o enxoval mais gasto "
        "da ala norte, que atende 14 residentes. As peças antigas viraram pano de limpeza. "
        "Com isso, o rodízio de troca diária voltou ao normal."),
    "Luvas e material de curativo": (
        "Curativos do mês garantidos",
        "A caixa de luvas e as gazes recebidas cobriram os curativos diários de oito residentes "
        "durante todo o mês, com sobra para o início do mês seguinte. Anexamos o registro de "
        "consumo assinado pela enfermagem."),
    "Cadeira de rodas e andadores": (
        "Cadeira de rodas em uso pela Dona Nair",
        "A cadeira passou por regulagem do freio com nosso fisioterapeuta e foi destinada a uma "
        "residente que usava equipamento emprestado. O andador ficou na sala de fisioterapia, "
        "usado no treino de marcha três vezes por semana."),
    "Fraldas geriátricas tamanho G": (
        "Fraldas recebidas e distribuídas",
        "Os quatro pacotes doados cobriram cinco dias de consumo dos residentes acamados. "
        "Mantemos o controle diário por residente, disponível para consulta de qualquer doador."),
    "Cobertores e mantas para o inverno": (
        "Trinta cobertores nos leitos",
        "Os 30 cobertores novos foram lavados, identificados e distribuídos nos leitos das duas "
        "alas. Os cobertores antigos, ainda em bom estado, foram doados para a Casa Renascer, "
        "que faz ronda noturna com pessoas em situação de rua."),
}

# Prestações das outras ONGs de demonstração
PRESTACOES_OUTRAS = {
    "Brinquedos pedagógicos": (
        "Jogos na sala de atividades",
        "Os jogos de encaixe e quebra-cabeças doados foram higienizados e já estão na sala de "
        "atividades, usados pelas turmas da tarde. Substituíram material que estava incompleto."),
    "Livros infantis e juvenis": (
        "Biblioteca com 20 títulos novos",
        "Os livros doados foram catalogados e entraram na roda de leitura das quartas-feiras. "
        "A estante da biblioteca chegou a 340 títulos."),
    "Cobertores e caminhas": (
        "Canis forrados para o inverno",
        "Os oito cobertores doados forraram os canis do setor dos animais idosos, que são os "
        "que mais sofrem com o frio. Nenhum animal passou frio neste inverno."),
    "Cobertores e sacos de dormir": (
        "Quatro pessoas atendidas na ronda",
        "Os cobertores foram distribuídos na ronda noturna de quarta-feira, no centro da cidade. "
        "Quatro pessoas receberam agasalho, e uma delas aceitou o encaminhamento para a casa de passagem."),
}

# Avaliações que a conta da feira RECEBEU das ONGs (reputação do doador)
AVALIACOES_DE_JOAO = [
    "Doador pontual e atencioso. Entregou tudo conforme combinado e ainda ajudou a conferir.",
    "Trouxe as luvas fora do horário de recepção e foi super compreensivo com nosso plantão.",
    "Conferiu peça por peça antes de doar. Material chegou impecável.",
    "Combinou a retirada e estava esperando no horário certo. Muito educado.",
    "Doação em ótimo estado e entrega no dia combinado. Doador de confiança.",
]

# Notificações não lidas da conta do doador (a tela abre com o sino aceso)
NOTIFICACOES_JOAO = [
    ("MATCH", "Seu interesse foi aceito",
     "O Abrigo Patinhas aceitou seu interesse em \"Ração para cães adultos\". Combine a entrega pelo chat.", 0, 2),
    ("MENSAGEM", "Nova mensagem",
     "Lar Viva respondeu sobre \"Suplemento alimentar e alimentos pastosos\".", 0, 4),
    ("PRESTACAO", "Prestação de contas publicada",
     "Lar Viva publicou a prestação de contas de \"Cadeira de rodas e andadores\".", 1, 22),
    ("CAMPANHA", "Nova campanha",
     "Lar Viva lançou a campanha \"Reforma dos banheiros da ala norte\".", 1, 34),
]

# Notificações do painel da ONG (o sino da Lar Viva também acende)
NOTIFICACOES_LARVIVA = [
    ("MATCH", "Novo interesse recebido",
     "Tech Solutions LTDA demonstrou interesse em \"Fraldas geriátricas tamanho G\".", 0, 0),
    ("MATCH", "Novo interesse recebido",
     "Ana Costa demonstrou interesse em \"Cobertores e mantas para o inverno\".", 0, 1),
    ("MENSAGEM", "Nova mensagem",
     "João Pereira enviou uma mensagem sobre \"Suplemento alimentar e alimentos pastosos\".", 1, 5),
]
