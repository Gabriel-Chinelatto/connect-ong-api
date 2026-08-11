# -*- coding: utf-8 -*-
"""Vocabulário da massa de demonstração do Connect ONG.

Tudo aqui é TEXTO que vai para o banco. O banco da escola é MySQL 5.6 com
colunas latin1: acentuação do português (á é í ó ú ã õ ç â ê ô à) funciona
normalmente, mas NÃO existe emoji, travessão longo nem aspas curvas. O
seed_demo.py valida isso antes de gravar (função checar_latin1) e aborta se
escapar algum caractere fora do latin1.

Regra de qualidade: nada de texto genérico. Cada descrição cita ano de
fundação, público atendido, bairro, programas e o que a instituição precisa,
e o gerador combina os pedaços de formas diferentes para que duas ONGs nunca
tenham o mesmo texto.
"""

# ---------------------------------------------------------------------------
# CAUSAS
# ---------------------------------------------------------------------------
# Cada causa descreve um tipo de instituição e alimenta nome, descrição,
# necessidades, campanhas, prestações e avaliações coerentes entre si.
# categorias = categorias CANÔNICAS do backend (util/Categorias.java):
#   Alimentos, Roupas, Higiene, Brinquedos, Educacao, Saude
# (os valores canônicos são gravados SEM acento de propósito, é o que o
#  backend normaliza; só as categorias seguem essa regra, o resto tem acento)

CAUSAS = [
    {
        "id": "criancas",
        "rotulo": "crianças e adolescentes",
        "nucleos": ["Criança Feliz", "Semente do Amanhã", "Pequenos Passos", "Estrela Guia",
                    "Primeiros Passos", "Crescer Juntos", "Girassol", "Casa da Criança",
                    "Raio de Sol", "Aconchego", "Bem-Me-Quer", "Pequeno Cidadão", "Vila Feliz",
                    "Recanto Infantil", "Nova Infância", "Sorriso Criança", "Semear"],
        "publico": ["crianças de 3 a 12 anos", "crianças e adolescentes de 6 a 17 anos",
                    "crianças em situação de vulnerabilidade social",
                    "adolescentes em contraturno escolar", "crianças encaminhadas pelo CRAS"],
        "programas": ["reforço escolar no contraturno", "oficinas de música e teatro",
                      "acompanhamento pedagógico individual", "aulas de informática básica",
                      "atividades esportivas orientadas", "roda de leitura semanal",
                      "oficina de horta e alimentação saudável", "apoio psicológico às famílias"],
        "categorias": ["Alimentos", "Brinquedos", "Educacao", "Higiene", "Roupas"],
        "necessidades": [
            ("Material escolar para o ano letivo", "Educacao",
             "Cadernos, lápis, canetas, borrachas e mochilas para {n} crianças atendidas no contraturno. Entregamos os kits na primeira semana de aula."),
            ("Leite em pó para o lanche da tarde", "Alimentos",
             "Servimos lanche todos os dias úteis. Precisamos de leite em pó integral, latas de 400g, para completar o cardápio do mês."),
            ("Brinquedos pedagógicos", "Brinquedos",
             "Jogos de encaixe, quebra-cabeças e brinquedos de montar para a sala de atividades. Podem ser usados, desde que completos e em bom estado."),
            ("Uniformes e tênis infantis", "Roupas",
             "Camisetas, bermudas e tênis dos tamanhos 4 ao 14. Muitas crianças chegam com o calçado furado, principalmente na época de chuva."),
            ("Kit de higiene infantil", "Higiene",
             "Sabonete, creme dental infantil, escova de dentes e xampu. Montamos {n} kits por mês e entregamos às famílias."),
            ("Livros infantis e juvenis", "Educacao",
             "Estamos montando a biblioteca da casa. Aceitamos livros usados em bom estado, de literatura infantil a juvenil."),
            ("Cadeiras e mesas para a sala de reforço", "Educacao",
             "Precisamos mobiliar mais uma sala para abrir turma nova no período da manhã."),
        ],
        "campanhas": [
            ("Volta às aulas", "Educacao", "Compra de {n} kits de material escolar completos para o início do ano letivo."),
            ("Natal sem fila", "Brinquedos", "Um presente para cada criança atendida, entregue na festa de fim de ano."),
            ("Lanche todo dia", "Alimentos", "Garantir o lanche da tarde por {meses} meses para as turmas do contraturno."),
        ],
        "avaliacoes": [
            "Levei o material escolar e fui recebido pela coordenadora, que mostrou as salas e explicou o projeto. Muito organizado.",
            "Retiraram a doação no horário combinado e ainda mandaram foto das crianças usando os jogos. Recomendo.",
            "Atendimento atencioso, responderam rápido pelo chat e a entrega foi tranquila.",
            "Já doei três vezes e sempre prestam contas do que foi feito. Instituição séria.",
            "Combinamos a entrega no sábado de manhã e estava tudo certo. Equipe simpática.",
        ],
        "prestacoes": [
            ("Kits escolares entregues", "Os {n} kits doados foram montados e entregues às crianças na primeira semana de aula. Sobrou material para repor cadernos durante o semestre."),
            ("Lanche reforçado por 30 dias", "Com o leite e os alimentos recebidos, servimos lanche completo a {n} crianças durante todo o mês."),
            ("Sala de leitura montada", "Os livros doados viraram a nossa biblioteca. As turmas fazem roda de leitura toda quarta-feira."),
        ],
    },
    {
        "id": "idosos",
        "rotulo": "pessoas idosas",
        "nucleos": ["Lar Viva", "Vida Sênior", "Casa Bem Viver", "Recanto dos Avós", "Vovó Feliz",
                    "Aconchego Sênior", "Casa Serena", "Idade de Ouro", "Refúgio da Paz",
                    "Lar São Vicente", "Casa do Vovô", "Doce Lar", "Outono Feliz", "Bem Querer"],
        "publico": ["idosos em situação de vulnerabilidade", "idosos sem vínculo familiar",
                    "idosos acamados e semidependentes", "idosos acima de 60 anos do bairro",
                    "idosos encaminhados pela rede de assistência"],
        "programas": ["fisioterapia duas vezes por semana", "oficina de memória e jogos",
                      "atendimento de enfermagem 24 horas", "grupo de convivência às quintas",
                      "acompanhamento médico mensal", "oficina de artesanato",
                      "baile da terceira idade uma vez por mês"],
        "categorias": ["Higiene", "Alimentos", "Saude", "Roupas"],
        "necessidades": [
            ("Fraldas geriátricas tamanho G", "Higiene",
             "Consumimos cerca de {n} fraldas por mês. É o item que mais pesa no nosso orçamento e o que mais falta."),
            ("Lençóis e toalhas de banho", "Roupas",
             "Trocamos a roupa de cama diariamente. Precisamos repor lençóis de solteiro e toalhas, novos ou usados em bom estado."),
            ("Suplemento alimentar", "Alimentos",
             "Vários residentes têm dificuldade de mastigação e dependem de suplemento e alimentos pastosos."),
            ("Luvas e material de curativo", "Saude",
             "Material básico de enfermagem para os curativos diários: luvas, gaze, soro fisiológico e esparadrapo."),
            ("Cobertores para o inverno", "Roupas",
             "As noites aqui ficam frias e temos {n} leitos. Precisamos de cobertores de casal e mantas."),
            ("Cadeira de rodas e andadores", "Saude",
             "Equipamentos de mobilidade em bom estado para uso interno. Aceitamos usados que ainda estejam firmes."),
        ],
        "campanhas": [
            ("Inverno sem frio", "Roupas", "Cobertores e agasalhos para os {n} residentes atravessarem o inverno."),
            ("Fralda todo mês", "Higiene", "Manter o estoque de fraldas geriátricas por {meses} meses."),
            ("Reforma da área de convivência", "Saude", "Trocar o piso e instalar barras de apoio no corredor e nos banheiros."),
        ],
        "avaliacoes": [
            "Fui entregar as fraldas e conheci a casa. Limpa, cheirosa e os idosos bem cuidados.",
            "A coordenadora agradeceu pessoalmente e mandou o comprovante do que foi usado. Transparência total.",
            "Atendimento humano de verdade. Voltarei a doar no próximo mês.",
            "Combinaram a retirada e chegaram no horário. Simples e sem burocracia.",
            "Doei cobertores no inverno passado e recebi foto dos idosos usando. Emocionante.",
        ],
        "prestacoes": [
            ("Fraldas recebidas e distribuídas", "As {n} fraldas doadas cobriram o consumo de {meses} semanas. Anexamos a planilha de uso por residente."),
            ("Cobertores nos leitos", "Todos os cobertores doados foram lavados, higienizados e já estão nos leitos dos residentes."),
            ("Material de enfermagem", "As luvas e gazes recebidas foram usadas nos curativos diários do mês. Estoque garantido até o fim do trimestre."),
        ],
    },
    {
        "id": "animais",
        "rotulo": "proteção animal",
        "nucleos": ["Abrigo Patinhas", "Amigos de Quatro Patas", "Focinho Feliz", "SOS Animais",
                    "Cão Sem Dono", "Patas Unidas", "Vira-Lata Feliz", "Arca Animal", "Late e Mia",
                    "Adote um Amigo", "Guardiões Animais", "Recanto Animal", "Rabo Abanando"],
        "publico": ["cães e gatos resgatados das ruas", "animais vítimas de maus-tratos",
                    "animais abandonados na região", "cães idosos sem adotante",
                    "gatos resgatados e castrados"],
        "programas": ["castração mensal a baixo custo", "feira de adoção aos sábados",
                      "lar temporário com voluntários", "tratamento veterinário dos resgatados",
                      "campanha de vacinação antirrábica", "vermifugação a cada trimestre"],
        "categorias": ["Alimentos", "Saude", "Higiene"],
        "necessidades": [
            ("Ração para cães adultos", "Alimentos",
             "Abrigamos {n} cães e consumimos cerca de {n2} kg de ração por mês. Aceitamos qualquer marca, inclusive pacote aberto."),
            ("Ração para gatos", "Alimentos",
             "Temos um setor só de felinos. Ração seca para gatos adultos é o que mais nos falta."),
            ("Vermífugo e antipulgas", "Saude",
             "Todo animal resgatado passa por vermifugação e controle de pulgas antes de ir para adoção."),
            ("Cobertores e caminhas", "Higiene",
             "No frio os canis precisam de forração. Aceitamos cobertores usados, toalhas velhas e tapetes."),
            ("Jornal e material de limpeza", "Higiene",
             "Usamos jornal na forração e água sanitária na limpeza diária dos canis."),
            ("Coleiras, guias e caixas de transporte", "Saude",
             "Material usado no transporte para a clínica e na entrega dos animais adotados."),
        ],
        "campanhas": [
            ("Castração solidária", "Saude", "Castrar {n} animais resgatados e de tutores de baixa renda do bairro."),
            ("Ração o ano todo", "Alimentos", "Garantir a ração dos {n} animais abrigados por {meses} meses."),
            ("Tratamento do resgate", "Saude", "Custear cirurgia e internação dos animais resgatados com maus-tratos."),
        ],
        "avaliacoes": [
            "Entreguei a ração e conheci o abrigo. Os animais estão limpos e bem alimentados.",
            "Pessoal muito dedicado, trabalham no voluntariado e ainda prestam contas de cada saco de ração.",
            "Responderam no mesmo dia e buscaram a doação em casa. Excelente.",
            "Adotei uma cadela aqui e depois virei doador. Trabalho sério.",
            "Mandaram foto dos gatos comendo a ração que doei. Vale muito a pena ajudar.",
        ],
        "prestacoes": [
            ("Ração consumida no mês", "Os {n2} kg de ração doados alimentaram os {n} cães do abrigo por {meses} semanas."),
            ("Castrações realizadas", "Com a campanha castramos {n} animais na clínica parceira. Anexamos as notas do procedimento."),
            ("Canis forrados no inverno", "Os cobertores doados forraram todos os canis. Nenhum animal passou frio neste inverno."),
        ],
    },
    {
        "id": "alimentacao",
        "rotulo": "combate à fome",
        "nucleos": ["Mesa da Esperança", "Prato Cheio", "Pão de Cada Dia", "Panela Solidária",
                    "Mesa Farta", "Cozinha do Bem", "Sopa Amiga", "Colheita Solidária",
                    "Mão na Massa", "Mesa Aberta", "Fome Zero Comunitário"],
        "publico": ["famílias em insegurança alimentar", "pessoas em situação de rua",
                    "famílias cadastradas no CadÚnico", "moradores do bairro em desemprego",
                    "famílias com crianças pequenas"],
        "programas": ["distribuição de cestas básicas todo mês", "sopa comunitária três vezes por semana",
                      "café da manhã para quem dorme na rua", "horta comunitária com as famílias",
                      "oficina de aproveitamento integral dos alimentos"],
        "categorias": ["Alimentos", "Higiene"],
        "necessidades": [
            ("Alimentos não perecíveis", "Alimentos",
             "Arroz, feijão, macarrão, óleo, açúcar e sal para montar as cestas do mês. Montamos {n} cestas."),
            ("Leite e achocolatado", "Alimentos",
             "Para o café da manhã das famílias com crianças pequenas. Leite em pó ou longa vida."),
            ("Legumes e verduras", "Alimentos",
             "Recebemos doação de hortifrúti três vezes por semana e distribuímos no mesmo dia, sem estoque."),
            ("Gás de cozinha", "Alimentos",
             "A cozinha comunitária consome dois botijões por semana para as {n} refeições servidas."),
            ("Marmitas e potes descartáveis", "Higiene",
             "Embalamos as refeições para quem busca na porta. Precisamos de marmitex e talheres descartáveis."),
            ("Produtos de limpeza da cozinha", "Higiene",
             "Detergente, água sanitária e desinfetante para manter a cozinha dentro das normas sanitárias."),
        ],
        "campanhas": [
            ("Cesta na mesa", "Alimentos", "Montar {n} cestas básicas completas para as famílias atendidas neste mês."),
            ("Sopa no inverno", "Alimentos", "Servir sopa quente três vezes por semana durante todo o inverno."),
            ("Cozinha equipada", "Alimentos", "Comprar fogão industrial e panelas para dobrar a produção de refeições."),
        ],
        "avaliacoes": [
            "Doei alimentos e vi as cestas sendo montadas na hora. Organização impecável.",
            "Distribuem tudo no mesmo dia, sem estoque parado. Deu para ver o impacto direto.",
            "Atenderam pelo chat rapidinho e combinaram a entrega no mesmo dia.",
            "Fila enorme de famílias sendo atendida com dignidade. Vale cada doação.",
            "Prestaram contas com foto das cestas entregues e a lista de famílias.",
        ],
        "prestacoes": [
            ("Cestas montadas e entregues", "Com os alimentos recebidos montamos {n} cestas básicas, entregues às famílias cadastradas no sábado."),
            ("Refeições servidas", "Foram {n} refeições servidas na cozinha comunitária com os mantimentos doados."),
            ("Distribuição de hortifrúti", "Os legumes doados foram distribuídos no mesmo dia a {n} famílias, sem desperdício."),
        ],
    },
    {
        "id": "educacao",
        "rotulo": "educação e cultura",
        "nucleos": ["Saber Livre", "Ler e Crescer", "Biblioteca Viva", "Escola Aberta",
                    "Ponto de Leitura", "Educar para Transformar", "Casa do Saber",
                    "Estudar Vale a Pena", "Portas do Conhecimento", "Nova Chance", "Letra Viva"],
        "publico": ["estudantes da rede pública", "jovens que prestam vestibular",
                    "adultos em alfabetização", "crianças com defasagem de aprendizagem",
                    "moradores do bairro sem acesso à internet"],
        "programas": ["cursinho pré-vestibular gratuito", "aulas de reforço de português e matemática",
                      "sala de informática aberta à comunidade", "clube de leitura semanal",
                      "alfabetização de adultos à noite", "oficina de redação para o Enem"],
        "categorias": ["Educacao", "Alimentos", "Higiene"],
        "necessidades": [
            ("Livros didáticos e apostilas", "Educacao",
             "Material do ensino médio e de pré-vestibular para os {n} alunos do cursinho gratuito."),
            ("Computadores para a sala de informática", "Educacao",
             "Aceitamos máquinas usadas que ainda liguem. Nossos voluntários fazem a manutenção e instalam o sistema."),
            ("Cadernos e material de escrita", "Educacao",
             "Cadernos universitários, canetas e lápis para as turmas do período da noite."),
            ("Lanche para as aulas da noite", "Alimentos",
             "Muitos alunos vêm direto do trabalho. Servimos um lanche simples antes da aula."),
            ("Cartucho de tinta e papel sulfite", "Educacao",
             "Imprimimos as listas de exercícios e simulados toda semana."),
            ("Mesas e cadeiras escolares", "Educacao",
             "Para abrir mais uma turma precisamos mobiliar a segunda sala."),
        ],
        "campanhas": [
            ("Cursinho de portas abertas", "Educacao", "Manter o cursinho gratuito funcionando por mais {meses} meses."),
            ("Sala de informática", "Educacao", "Montar uma sala com {n} computadores para a comunidade."),
            ("Simulado do Enem", "Educacao", "Custear a impressão dos simulados e o lanche dos {n} alunos no dia da prova."),
        ],
        "avaliacoes": [
            "Doei livros e me mostraram a sala de aula cheia. Projeto que muda vida.",
            "Organização exemplar, receberam o material e já catalogaram na biblioteca.",
            "Contato rápido e direto pelo aplicativo, entrega sem complicação.",
            "Meu filho estudou aqui e passou na federal. Hoje eu doo todo mês.",
            "Prestaram contas com a lista de alunos beneficiados. Sério e transparente.",
        ],
        "prestacoes": [
            ("Biblioteca reforçada", "Os livros doados foram catalogados e já estão em uso pelas turmas do cursinho."),
            ("Sala de informática ampliada", "Recebemos as máquinas, recuperamos {n} delas e a sala agora atende {n2} alunos por semana."),
            ("Material distribuído", "Cada aluno das turmas da noite recebeu caderno e kit de escrita. Sobrou reserva para os novos matriculados."),
        ],
    },
    {
        "id": "saude",
        "rotulo": "saúde e apoio a pacientes",
        "nucleos": ["Casa de Apoio Vida", "Amparo Saúde", "Rede do Bem-Estar", "Casa Acolher",
                    "Vida Nova", "Coração Solidário", "Apoio Vida", "Casa Esperança",
                    "Grupo de Apoio Renascer", "Mão Amiga"],
        "publico": ["pacientes em tratamento fora do domicílio", "famílias de pacientes internados",
                    "pessoas em tratamento oncológico", "pacientes que dependem do SUS",
                    "gestantes em acompanhamento"],
        "programas": ["hospedagem gratuita para pacientes de outras cidades",
                      "transporte até o hospital de referência", "apoio psicológico às famílias",
                      "distribuição de medicamentos básicos", "grupo de apoio semanal"],
        "categorias": ["Saude", "Higiene", "Alimentos", "Roupas"],
        "necessidades": [
            ("Itens de higiene pessoal", "Higiene",
             "Montamos kits para os pacientes hospedados: sabonete, escova, creme dental e papel higiênico."),
            ("Alimentos para a casa de apoio", "Alimentos",
             "Servimos três refeições por dia para {n} hóspedes. Precisamos de mantimentos básicos."),
            ("Lençóis e toalhas", "Roupas",
             "A casa tem {n} leitos e a roupa de cama é trocada a cada dois dias."),
            ("Máscaras e álcool em gel", "Saude",
             "Nossos hóspedes estão imunossuprimidos e a proteção é obrigatória dentro da casa."),
            ("Vale-transporte para os pacientes", "Saude",
             "Cada paciente faz em média {n} viagens ao hospital por semana."),
            ("Lenços e toucas para pacientes em quimioterapia", "Roupas",
             "Item simples que faz muita diferença na autoestima de quem está em tratamento."),
        ],
        "campanhas": [
            ("Casa cheia, mesa posta", "Alimentos", "Garantir as refeições dos hóspedes por {meses} meses."),
            ("Transporte até o hospital", "Saude", "Custear o transporte de {n} pacientes às sessões de tratamento."),
            ("Reforma dos quartos", "Saude", "Trocar colchões e pintar os {n} quartos da casa de apoio."),
        ],
        "avaliacoes": [
            "Casa impecável, recebem pacientes de toda a região. Doar aqui faz diferença real.",
            "Fui bem atendido, explicaram o trabalho e mostraram os quartos. Transparência total.",
            "Responderam rápido e ainda agradeceram por mensagem depois da entrega.",
            "Minha tia ficou hospedada durante o tratamento. Hoje retribuo doando.",
            "Prestação de contas detalhada, com foto do que foi comprado.",
        ],
        "prestacoes": [
            ("Kits de higiene entregues", "Foram montados {n} kits de higiene, um para cada paciente hospedado no mês."),
            ("Refeições servidas na casa", "Com os alimentos doados servimos {n} refeições aos pacientes e acompanhantes."),
            ("Transporte custeado", "O valor arrecadado cobriu {n} viagens de pacientes ao hospital de referência."),
        ],
    },
    {
        "id": "moradia",
        "rotulo": "população em situação de rua",
        "nucleos": ["Casa Renascer", "Abrigo Novo Amanhecer", "Ponto de Apoio", "Casa de Passagem Luz",
                    "Rua Acolhida", "Reviver", "Casa Aberta", "Recomeço", "Albergue Solidário",
                    "Movimento Rua Digna", "Primeiro Teto"],
        "publico": ["pessoas em situação de rua", "famílias desalojadas",
                    "pessoas em processo de reinserção social", "migrantes recém-chegados à cidade",
                    "pessoas saindo de tratamento de dependência"],
        "programas": ["banho e lavanderia todos os dias", "encaminhamento para documentação e emprego",
                      "pernoite com {n} vagas", "distribuição de marmitas à noite",
                      "oficina de capacitação e primeiro emprego", "acompanhamento com assistente social"],
        "categorias": ["Roupas", "Higiene", "Alimentos"],
        "necessidades": [
            ("Roupas masculinas adultas", "Roupas",
             "Calças, camisetas e casacos tamanhos M, G e GG. É o que mais sai no atendimento diário."),
            ("Cobertores e sacos de dormir", "Roupas",
             "Distribuímos nas rondas noturnas de inverno. Aceitamos usados em bom estado."),
            ("Kit de higiene para banho", "Higiene",
             "Sabonete, xampu, desodorante e barbeador descartável. Oferecemos {n} banhos por dia."),
            ("Tênis e chinelos", "Roupas",
             "Calçado adulto, números 38 ao 44. Quem vive na rua acaba com o pé machucado."),
            ("Marmitas e alimentos prontos", "Alimentos",
             "Nossa ronda noturna distribui refeições três vezes por semana."),
            ("Sabão em pó e material de lavanderia", "Higiene",
             "A lavanderia atende {n} pessoas por dia e consome muito sabão e água sanitária."),
        ],
        "campanhas": [
            ("Noite sem frio", "Roupas", "Cobertores e agasalhos para as rondas noturnas do inverno."),
            ("Banho e dignidade", "Higiene", "Manter os kits de higiene e a lavanderia por {meses} meses."),
            ("Documento e trabalho", "Educacao", "Custear a segunda via de documentos de {n} pessoas atendidas."),
        ],
        "avaliacoes": [
            "Trabalho difícil e feito com muito respeito. Entreguei roupas e fui muito bem recebido.",
            "Vi a ronda noturna de perto. Gente seríssima, a doação chega em quem precisa.",
            "Resposta rápida pelo chat e retirada combinada sem burocracia.",
            "Prestaram contas de cada peça distribuída. Nunca vi isso em outra instituição.",
            "Doei tênis e me mandaram foto do rapaz que recebeu. Emocionante.",
        ],
        "prestacoes": [
            ("Agasalhos distribuídos na ronda", "As {n} peças doadas foram distribuídas nas rondas noturnas de junho e julho."),
            ("Banhos e kits do mês", "Foram {n} banhos oferecidos e {n2} kits de higiene entregues com o material doado."),
            ("Marmitas entregues", "As refeições doadas viraram {n} marmitas distribuídas no centro da cidade."),
        ],
    },
    {
        "id": "mulheres",
        "rotulo": "apoio a mulheres",
        "nucleos": ["Casa Marias", "Rede Mulher", "Espaço Delas", "Casa Florescer",
                    "Mulher Segura", "Coletivo Aurora", "Casa Lilás", "Nós por Elas",
                    "Movimento Mães Unidas", "Casa Girassol"],
        "publico": ["mulheres em situação de violência doméstica", "mães solo em vulnerabilidade",
                    "gestantes sem apoio familiar", "mulheres em busca do primeiro emprego",
                    "mulheres chefes de família do bairro"],
        "programas": ["acolhimento sigiloso com {n} vagas", "orientação jurídica gratuita",
                      "grupo de apoio psicológico semanal", "curso de capacitação profissional",
                      "creche para as crianças durante os cursos", "oficina de geração de renda"],
        "categorias": ["Higiene", "Roupas", "Alimentos", "Educacao"],
        "necessidades": [
            ("Absorventes higiênicos", "Higiene",
             "Distribuímos kits mensais. A pobreza menstrual é um dos problemas mais silenciosos que atendemos."),
            ("Enxoval de bebê", "Roupas",
             "Roupinhas, fraldas e itens de berço para as gestantes acolhidas."),
            ("Cestas básicas para mães solo", "Alimentos",
             "Atendemos {n} famílias chefiadas por mulheres, muitas sem renda fixa."),
            ("Material para os cursos de capacitação", "Educacao",
             "Tecidos, linhas e material de papelaria para as oficinas de geração de renda."),
            ("Roupas femininas adultas", "Roupas",
             "Muitas chegam ao acolhimento apenas com a roupa do corpo."),
            ("Produtos de higiene e beleza", "Higiene",
             "Sabonete, xampu, creme e desodorante para os kits de acolhimento."),
        ],
        "campanhas": [
            ("Dignidade menstrual", "Higiene", "Distribuir {n} kits de absorventes para mulheres do bairro."),
            ("Enxoval solidário", "Roupas", "Montar enxoval completo para {n} gestantes acolhidas."),
            ("Renda própria", "Educacao", "Custear a oficina de costura e o material das {n} alunas."),
        ],
        "avaliacoes": [
            "Trabalho sério e sigiloso. Entreguei a doação no ponto combinado, tudo muito bem organizado.",
            "Recebi retorno detalhado do que foi feito com a doação. Instituição de confiança.",
            "Atendimento humano e rápido pelo chat. Doei e já quero doar de novo.",
            "Conheci o projeto pela feira do bairro e virei doadora fixa.",
            "Prestação de contas com nota fiscal do que foi comprado. Exemplar.",
        ],
        "prestacoes": [
            ("Kits de dignidade entregues", "Foram montados e distribuídos {n} kits de absorventes e higiene para as mulheres atendidas."),
            ("Enxovais montados", "As doações viraram {n} enxovais completos, entregues às gestantes acolhidas no trimestre."),
            ("Oficina de costura", "O material doado abasteceu a oficina, onde {n} alunas produziram peças para venda própria."),
        ],
    },
    {
        "id": "deficiencia",
        "rotulo": "pessoas com deficiência",
        "nucleos": ["Incluir para Transformar", "Espaço Inclusivo", "Casa Sem Barreiras",
                    "Movimento Acessível", "Instituto Igualdade", "Caminho Livre",
                    "Todos Podem", "Ponte Inclusiva", "Vencer Limites", "Passo Livre"],
        "publico": ["crianças com deficiência intelectual", "pessoas com deficiência física",
                    "crianças no espectro autista", "jovens com síndrome de Down",
                    "famílias de pessoas com deficiência"],
        "programas": ["terapia ocupacional duas vezes por semana", "fonoaudiologia e fisioterapia",
                      "oficina de vida diária e autonomia", "apoio às famílias e cuidadores",
                      "atividades adaptadas de esporte", "acompanhamento pedagógico especializado"],
        "categorias": ["Saude", "Educacao", "Brinquedos", "Higiene"],
        "necessidades": [
            ("Material de terapia ocupacional", "Saude",
             "Massinhas, bolas sensoriais, encaixes e material adaptado para as sessões semanais."),
            ("Brinquedos sensoriais", "Brinquedos",
             "Brinquedos com textura, som e luz para as salas de estimulação das crianças atendidas."),
            ("Fraldas tamanho G e XG", "Higiene",
             "Muitos dos nossos atendidos usam fralda em idade escolar. É um custo alto para as famílias."),
            ("Material pedagógico adaptado", "Educacao",
             "Pranchas de comunicação, livros com pictogramas e jogos adaptados."),
            ("Cadeiras de rodas e órteses", "Saude",
             "Equipamentos usados em bom estado ajudam muitas famílias que esperam pelo SUS."),
            ("Tablets para comunicação alternativa", "Educacao",
             "Usamos aplicativos de comunicação com os alunos não verbais."),
        ],
        "campanhas": [
            ("Sala de estimulação", "Saude", "Montar a sala sensorial com material adaptado para {n} crianças."),
            ("Terapia todo mês", "Saude", "Manter as sessões de terapia ocupacional por {meses} meses."),
            ("Comunicar é um direito", "Educacao", "Comprar {n} tablets com aplicativo de comunicação alternativa."),
        ],
        "avaliacoes": [
            "Profissionais preparados e estrutura adaptada de verdade. Doação muito bem aproveitada.",
            "Levei os brinquedos sensoriais e já estavam em uso na semana seguinte.",
            "Atendimento atencioso, tiraram todas as minhas dúvidas pelo chat.",
            "Meu sobrinho é atendido aqui. Trabalho que transforma famílias inteiras.",
            "Prestaram contas com foto das crianças usando o material. Muito bom.",
        ],
        "prestacoes": [
            ("Sala sensorial equipada", "Os brinquedos e materiais doados equiparam a sala de estimulação, usada por {n} crianças por semana."),
            ("Fraldas do trimestre", "As {n} fraldas doadas atenderam {n2} famílias e cobriram três meses de uso."),
            ("Material adaptado em uso", "As pranchas e jogos adaptados já estão nas sessões de fono e terapia ocupacional."),
        ],
    },
    {
        "id": "ambiente",
        "rotulo": "meio ambiente",
        "nucleos": ["Instituto Eco Verde", "Raízes do Amanhã", "Planta Vida", "Rio Limpo",
                    "Mata Viva", "Semear Verde", "Coletivo Reciclar", "Verde Comunidade",
                    "Guardiões da Mata", "Água Viva"],
        "publico": ["escolas públicas da região", "moradores de bairros sem coleta seletiva",
                    "catadores de material reciclável", "comunidades ribeirinhas",
                    "áreas de nascente degradadas"],
        "programas": ["mutirão de plantio de mudas nativas", "oficina de compostagem nas escolas",
                      "coleta seletiva no bairro", "recuperação de nascentes",
                      "educação ambiental em escolas públicas", "horta comunitária"],
        "categorias": ["Educacao", "Alimentos", "Higiene"],
        "necessidades": [
            ("Mudas de árvores nativas", "Alimentos",
             "Plantamos {n} mudas por mutirão na área de nascente que estamos recuperando."),
            ("Ferramentas de jardinagem", "Educacao",
             "Enxadas, pás, regadores e luvas para os mutirões de plantio com voluntários."),
            ("Material para oficinas nas escolas", "Educacao",
             "Cartolina, tinta e material reciclável para as oficinas de educação ambiental."),
            ("Sacos e luvas para coleta", "Higiene",
             "Fazemos limpeza de margens de rio uma vez por mês com {n} voluntários."),
            ("Composteiras e baldes", "Educacao",
             "Distribuímos composteiras domésticas para as famílias que participam das oficinas."),
        ],
        "campanhas": [
            ("Mil mudas no bairro", "Educacao", "Plantar {n} mudas nativas na área de proteção do córrego."),
            ("Escola verde", "Educacao", "Levar a oficina de compostagem para {n} escolas públicas."),
            ("Rio limpo", "Higiene", "Custear os mutirões de limpeza das margens por {meses} meses."),
        ],
        "avaliacoes": [
            "Participei do mutirão depois de doar as ferramentas. Trabalho organizado e com resultado visível.",
            "Doei mudas e recebi foto do plantio no mês seguinte. Muito bom.",
            "Resposta rápida e agendamento fácil pelo aplicativo.",
            "Projeto sério, já recuperaram uma nascente inteira aqui perto.",
            "Prestação de contas com fotos do antes e depois da área recuperada.",
        ],
        "prestacoes": [
            ("Mutirão de plantio", "As {n} mudas doadas foram plantadas na área de nascente com {n2} voluntários no último sábado."),
            ("Oficinas nas escolas", "O material doado atendeu {n} oficinas de educação ambiental em escolas da rede pública."),
            ("Limpeza das margens", "Foram recolhidos vários sacos de resíduos das margens do córrego com o material doado."),
        ],
    },
    {
        "id": "trabalho",
        "rotulo": "capacitação e trabalho",
        "nucleos": ["Novos Caminhos", "Primeiro Emprego", "Oficina do Futuro", "Renda Digna",
                    "Capacita Comunidade", "Mão que Trabalha", "Ponte para o Trabalho",
                    "Jovem Aprendiz Comunitário", "Trilha Profissional", "Chance Real"],
        "publico": ["jovens de 16 a 24 anos sem experiência", "adultos desempregados do bairro",
                    "pessoas egressas do sistema prisional", "mães que precisam de renda própria",
                    "jovens que buscam o primeiro emprego"],
        "programas": ["curso de informática básica", "oficina de panificação e confeitaria",
                      "curso de eletricista predial", "oficina de costura industrial",
                      "preparação para entrevista e currículo", "encaminhamento a vagas de parceiros"],
        "categorias": ["Educacao", "Alimentos", "Roupas"],
        "necessidades": [
            ("Computadores para o curso de informática", "Educacao",
             "Máquinas usadas que ainda liguem. Formamos {n} alunos por turma, três turmas por ano."),
            ("Material para a oficina de panificação", "Alimentos",
             "Farinha, fermento e utensílios para as aulas práticas da turma de confeitaria."),
            ("Tecidos e linhas para a costura", "Educacao",
             "A oficina de costura forma {n} alunas por semestre e depende de doação de material."),
            ("Roupas sociais para entrevistas", "Roupas",
             "Camisas, calças e sapatos sociais para os alunos irem bem vestidos às entrevistas."),
            ("Ferramentas para o curso de elétrica", "Educacao",
             "Alicates, multímetros e material didático para as aulas práticas."),
        ],
        "campanhas": [
            ("Turma formada", "Educacao", "Custear o material das {n} vagas da próxima turma de capacitação."),
            ("Vestido para a vaga", "Roupas", "Montar {n} kits de roupa social para os alunos que vão a entrevistas."),
            ("Oficina equipada", "Educacao", "Comprar máquinas de costura para dobrar as vagas da oficina."),
        ],
        "avaliacoes": [
            "Doei computadores e em duas semanas já estavam em uso na sala de aula.",
            "Projeto que resolve de verdade: os alunos saem empregados. Vale a pena apoiar.",
            "Contato rápido, entrega combinada por chat e recebida no horário.",
            "Meu filho fez o curso aqui e está trabalhando. Doar é retribuir.",
            "Prestação de contas com a lista de alunos formados no semestre.",
        ],
        "prestacoes": [
            ("Turma formada com o material doado", "Os {n} alunos da turma concluíram o curso usando o material recebido. {n2} já foram encaminhados a vagas."),
            ("Sala de informática renovada", "As máquinas doadas substituíram os computadores antigos. A sala atende {n} alunos por semana."),
            ("Kits de roupa social", "Foram montados {n} kits de roupa social, entregues aos alunos que participaram do mutirão de entrevistas."),
        ],
    },
]

# ---------------------------------------------------------------------------
# NOMES DE INSTITUIÇÃO
# ---------------------------------------------------------------------------
PREFIXOS = ["Instituto", "Associação", "Casa", "Centro", "Projeto", "Fundação", "Grupo",
            "Núcleo", "Espaço", "Movimento", "Sociedade", "Obra Social", "Missão",
            "Ação", "Coletivo", "Rede", "Lar", "União"]

COMPLEMENTOS = ["Comunitário", "Solidário", "do Bem", "Solidária", "Comunitária",
                "de Apoio", "Beneficente", "Social", "Voluntário"]

# ---------------------------------------------------------------------------
# DESCRIÇÃO DA ONG - moldes combináveis
# ---------------------------------------------------------------------------
MOLDES_DESCRICAO = [
    "Fundada em {ano}, a instituição atende {atendidos} {publico} no bairro {bairro}, em {cidade}. "
    "Mantém {prog1} e {prog2}, com equipe de {vol} voluntários e {prof} profissionais contratados. "
    "As doações de {item} são o que sustenta o atendimento diário.",

    "Atuamos desde {ano} em {cidade}, atendendo {atendidos} {publico}. "
    "Nossa rotina inclui {prog1}, além de {prog2}. "
    "A casa funciona de segunda a sexta, das 8h às 17h, e depende de doações de {item} para não interromper o serviço.",

    "A instituição nasceu em {ano} da mobilização de moradores do bairro {bairro}, em {cidade}. "
    "Hoje acompanha {atendidos} {publico} e oferece {prog1}. "
    "Somos mantidos por doações e por {vol} voluntários; {prof} profissionais garantem o atendimento técnico.",

    "Organização sem fins lucrativos de {cidade}, em atividade desde {ano}. "
    "Atende {atendidos} {publico} com {prog1} e {prog2}. "
    "Todo o trabalho é gratuito e financiado por doações da comunidade, principalmente de {item}.",

    "Somos uma casa de porta aberta no bairro {bairro}, em {cidade}, funcionando desde {ano}. "
    "Recebemos {atendidos} {publico}, com {prog1} durante a semana e {prog2} aos sábados. "
    "Precisamos de doação constante de {item} para manter o ritmo do atendimento.",

    "Desde {ano} trabalhamos em {cidade} com {publico}. "
    "São {atendidos} atendidos por mês, {prog1} e {prog2}. "
    "A equipe reúne {vol} voluntários da comunidade e {prof} profissionais. Prestamos contas de cada doação recebida.",

    "Instituição filantrópica de {cidade}, registrada em {ano} e sediada no bairro {bairro}. "
    "Atendemos {atendidos} {publico}, com {prog1}. "
    "Não cobramos nada das famílias: tudo vem de doação, em especial de {item}.",
]

BAIRROS = ["Centro", "Jardim São José", "Vila Nova", "Jardim América", "Santa Rita",
           "Vila Industrial", "Jardim Paulista", "São Benedito", "Bela Vista", "Vila Rica",
           "Parque das Nações", "Jardim Primavera", "São Cristóvão", "Boa Vista", "Vila União",
           "Cidade Nova", "Jardim das Palmeiras", "Santo Antônio", "Vila Esperança",
           "Parque Industrial", "Jardim Bandeirantes", "São Judas", "Nova Esperança",
           "Vila São Pedro", "Jardim Aeroporto", "Alto da Boa Vista", "Jardim Europa"]

VIAS = ["Rua XV de Novembro", "Avenida Brasil", "Rua Sete de Setembro", "Rua São Paulo",
        "Avenida Getúlio Vargas", "Rua Santos Dumont", "Rua Dom Pedro II", "Avenida Rio Branco",
        "Rua Tiradentes", "Rua Marechal Deodoro", "Avenida Independência", "Rua Barão do Rio Branco",
        "Rua Duque de Caxias", "Rua Coronel Fernando Prestes", "Avenida das Nações",
        "Rua São João", "Rua Padre Anchieta", "Avenida São Carlos", "Rua Amazonas",
        "Rua Minas Gerais", "Rua da Matriz", "Avenida Presidente Vargas", "Rua Bahia",
        "Rua Ceará", "Rua Paraná", "Avenida Central", "Rua Goiás", "Rua Piauí"]

# ---------------------------------------------------------------------------
# PESSOAS (doadores)
# ---------------------------------------------------------------------------
NOMES_M = ["João", "Pedro", "Lucas", "Gabriel", "Rafael", "Matheus", "Bruno", "Felipe",
           "Gustavo", "Rodrigo", "Thiago", "André", "Marcelo", "Eduardo", "Vinícius",
           "Leonardo", "Fernando", "Ricardo", "Paulo", "Carlos", "Antônio", "Marcos",
           "Daniel", "Diego", "Caio", "Henrique", "Igor", "Murilo", "Otávio", "Renato",
           "Samuel", "Vitor", "Alexandre", "Fábio", "Guilherme", "José", "Luiz", "Sérgio"]
NOMES_F = ["Maria", "Ana", "Júlia", "Beatriz", "Larissa", "Camila", "Fernanda", "Amanda",
           "Patrícia", "Carolina", "Juliana", "Mariana", "Letícia", "Gabriela", "Bruna",
           "Isabela", "Vanessa", "Renata", "Aline", "Priscila", "Tatiane", "Simone",
           "Daniela", "Rafaela", "Natália", "Cristiane", "Adriana", "Sandra", "Luciana",
           "Márcia", "Elaine", "Sílvia", "Regina", "Cláudia", "Mônica", "Rosana", "Helena"]
SOBRENOMES = ["Silva", "Santos", "Oliveira", "Souza", "Rodrigues", "Ferreira", "Alves",
              "Pereira", "Lima", "Gomes", "Costa", "Ribeiro", "Martins", "Carvalho",
              "Almeida", "Lopes", "Soares", "Fernandes", "Vieira", "Barbosa", "Rocha",
              "Dias", "Nunes", "Moreira", "Cardoso", "Teixeira", "Correia", "Mendes",
              "Araújo", "Cavalcanti", "Monteiro", "Freitas", "Pinto", "Machado", "Ramos",
              "Azevedo", "Batista", "Duarte", "Nascimento", "Moraes", "Campos", "Farias"]

BIOS_DOADOR = [
    "Doo o que sobra em casa desde que descobri o aplicativo. Prefiro entregar pessoalmente.",
    "Professor da rede pública. Costumo doar livros e material escolar.",
    "Moro sozinha e sempre tenho roupa em bom estado para passar adiante.",
    "Trabalho com comércio e separo os itens que sobram do estoque todo mês.",
    "Acredito que doar é mais fácil quando a gente sabe para onde vai.",
    "Faço parte de um grupo de vizinhos que arrecada alimentos toda semana.",
    "Doador desde 2025. Gosto de acompanhar a prestação de contas.",
    "Aposentado, tenho tempo para levar as doações pessoalmente.",
    "Doo principalmente ração. Tenho quatro gatos resgatados em casa.",
    "Estudante de enfermagem. Ajudo com itens de higiene sempre que posso.",
    "Prefiro doar para ONGs perto de casa, para poder visitar e conhecer o trabalho.",
    "Comecei doando roupas do meu filho que ficaram pequenas e não parei mais.",
    "Empreendedora. Todo mês destino uma parte do faturamento para uma causa.",
    "Gosto de doar em campanhas de inverno. É quando mais precisam.",
    "Meu objetivo é fazer pelo menos uma doação por mês.",
    "", "", "",  # muita gente real não preenche a bio
]

# ---------------------------------------------------------------------------
# ITENS QUE O DOADOR CADASTRA PARA DOAR (tabela doacao)
# ---------------------------------------------------------------------------
ITENS_DOACAO = [
    ("Cesta básica completa", "Alimentos", "Arroz, feijão, macarrão, óleo, açúcar, café e sal."),
    ("Pacote de arroz 5kg", "Alimentos", "Fechado, dentro da validade."),
    ("Leite em pó", "Alimentos", "Duas latas de 400g, lacradas."),
    ("Ração para cães 15kg", "Alimentos", "Pacote fechado, marca premium."),
    ("Ração para gatos", "Alimentos", "Três pacotes de 1kg."),
    ("Agasalhos adultos", "Roupas", "Cinco casacos em bom estado, tamanhos M e G."),
    ("Roupas infantis", "Roupas", "Sacola com roupas de 4 a 10 anos, lavadas e dobradas."),
    ("Cobertor de casal", "Roupas", "Usado uma temporada, sem furos."),
    ("Tênis masculino 41", "Roupas", "Pouco uso, sola em bom estado."),
    ("Kit de higiene", "Higiene", "Sabonete, xampu, creme dental e escova."),
    ("Fraldas geriátricas G", "Higiene", "Pacote fechado com 20 unidades."),
    ("Absorventes", "Higiene", "Seis pacotes lacrados."),
    ("Material escolar", "Educacao", "Cadernos, canetas, lápis e uma mochila nova."),
    ("Livros infantis", "Educacao", "Quinze livros de literatura infantil em bom estado."),
    ("Notebook usado", "Educacao", "Funcionando, com carregador. Bateria fraca."),
    ("Brinquedos variados", "Brinquedos", "Caixa com jogos e bonecos, todos completos."),
    ("Quebra-cabeça e jogos", "Brinquedos", "Quatro jogos de tabuleiro completos."),
    ("Fraldas infantis M", "Higiene", "Dois pacotes fechados."),
    ("Cadeira de rodas", "Saude", "Usada, dobrável, com pneus bons."),
    ("Muletas e andador", "Saude", "Ambos em bom estado de conservação."),
]

# ---------------------------------------------------------------------------
# CHAT - trocas de mensagem realistas entre doador e ONG
# ---------------------------------------------------------------------------
# Cada roteiro é uma lista de (remetente, texto). {item} = título da necessidade.
ROTEIROS_CHAT = [
    [("DOADOR", "Boa tarde! Tenho interesse em ajudar com {item}. Ainda estão precisando?"),
     ("ONG", "Boa tarde! Estamos sim, e seria uma ajuda enorme. Você prefere trazer até a casa ou quer que a gente busque?"),
     ("DOADOR", "Consigo levar aí. Qual o melhor horário?"),
     ("ONG", "De segunda a sexta até as 17h, ou sábado de manhã. Estamos na {endereco}."),
     ("DOADOR", "Perfeito, vou no sábado por volta das 10h então."),
     ("ONG", "Combinado! Vou deixar avisado na portaria. Muito obrigada mesmo."),
     ("DOADOR", "Acabei de deixar com a moça da recepção. Espero que ajude!"),
     ("ONG", "Chegou tudo certinho, já conferimos. Você não imagina a diferença que isso faz aqui. Obrigada!")],

    [("DOADOR", "Olá! Vi a necessidade de {item} e quero contribuir. Quantos ainda faltam?"),
     ("ONG", "Olá! Falta cerca de metade do que pedimos. Qualquer quantidade ajuda."),
     ("DOADOR", "Consigo uma parte esta semana. Vocês aceitam usado em bom estado?"),
     ("ONG", "Aceitamos sim, desde que esteja limpo e inteiro. É o que mais recebemos."),
     ("DOADOR", "Ótimo. Separo hoje à noite e levo amanhã depois do trabalho, por volta das 18h."),
     ("ONG", "Até as 17h tem sempre alguém. Se atrasar me avisa por aqui que eu espero."),
     ("DOADOR", "Cheguei às 17h em ponto, deu certo. Obrigado pela atenção!"),
     ("ONG", "Recebemos, muito obrigada! Já separamos para a entrega de sexta.")],

    [("DOADOR", "Boa noite. Posso doar {item}? Moro em outro bairro, dá para combinar a retirada?"),
     ("ONG", "Boa noite! Dá sim. Temos um voluntário que faz as retiradas às terças e quintas."),
     ("DOADOR", "Terça de manhã seria ótimo para mim."),
     ("ONG", "Fechado. Pode me passar o endereço e um ponto de referência?"),
     ("DOADOR", "Claro, mando aqui. Fico no portão azul, é só tocar a campainha."),
     ("ONG", "Anotado! O voluntário passa entre 9h e 11h."),
     ("DOADOR", "Ele passou aqui às 9h30, tudo certo. Muito educado."),
     ("ONG", "Que bom! Chegou aqui e já foi para o estoque. Obrigada pela confiança.")],

    [("DOADOR", "Oi! Trabalho perto de vocês e queria ajudar com {item}."),
     ("ONG", "Oi! Toda ajuda é bem-vinda. Estamos com o estoque bem baixo esse mês."),
     ("DOADOR", "Posso levar hoje no fim do expediente?"),
     ("ONG", "Pode sim, ficamos até as 18h hoje."),
     ("DOADOR", "Deixei na portaria às 17h50. Falei com o seu Antônio."),
     ("ONG", "Confirmado, ele já trouxe para dentro. Muito obrigada!")],

    [("DOADOR", "Olá, tudo bem? Vi que vocês precisam de {item}. Quero doar e conhecer o trabalho de vocês."),
     ("ONG", "Tudo ótimo! Será um prazer receber. Fazemos visita guiada quando alguém quer conhecer."),
     ("DOADOR", "Adorei a ideia. Posso ir no sábado?"),
     ("ONG", "Pode! Sábado de manhã temos as atividades acontecendo, é o melhor dia para ver de perto."),
     ("DOADOR", "Estive aí hoje, obrigado pela recepção. Fiquei impressionado com a organização."),
     ("ONG", "Nós que agradecemos a visita e a doação. Volte sempre!")],

    [("DOADOR", "Bom dia! Tenho {item} para doar, mas só consigo entregar no fim do mês. Serve?"),
     ("ONG", "Bom dia! Serve sim, não tem problema nenhum. Deixo reservado aqui no sistema."),
     ("DOADOR", "Combinado então, aviso alguns dias antes."),
     ("ONG", "Perfeito, fico no aguardo. Obrigada por avisar com antecedência."),
     ("DOADOR", "Chegou o fim do mês! Posso levar na sexta?"),
     ("ONG", "Pode sim, estaremos aqui. Obrigada por não esquecer da gente!"),
     ("DOADOR", "Entregue! Espero que ajude bastante."),
     ("ONG", "Ajudou demais. Já está tudo separado para a distribuição da próxima semana.")],
]

# Conversas de match ainda em andamento (não concluído)
ROTEIROS_CHAT_ABERTO = [
    [("DOADOR", "Oi! Ainda estão precisando de {item}?"),
     ("ONG", "Oi! Estamos sim. Você consegue trazer até a casa?")],
    [("DOADOR", "Boa tarde, tenho interesse em doar {item}. Como funciona?"),
     ("ONG", "Boa tarde! É simples: você traz aqui ou a gente busca, se for na cidade."),
     ("DOADOR", "Legal. Vou ver meu horário esta semana e te aviso.")],
    [("DOADOR", "Olá, quero ajudar com {item}. Qual o endereço?"),
     ("ONG", "Olá! Estamos na {endereco}. De segunda a sexta até as 17h.")],
]

# ---------------------------------------------------------------------------
# NOTIFICAÇÕES
# ---------------------------------------------------------------------------
NOTIF_MATCH_ACEITO = ("Seu interesse foi aceito",
                      "A ONG {ong} aceitou seu interesse em \"{item}\". Combine a entrega pelo chat.")
NOTIF_MATCH_NOVO = ("Novo interesse recebido",
                    "{doador} demonstrou interesse em \"{item}\".")
NOTIF_PRESTACAO = ("Prestação de contas publicada",
                   "A ONG {ong} publicou a prestação de contas de \"{item}\".")
NOTIF_MENSAGEM = ("Nova mensagem",
                  "{quem} enviou uma mensagem sobre \"{item}\".")
NOTIF_CAMPANHA = ("Nova campanha",
                  "A ONG {ong} lançou a campanha \"{campanha}\".")


def checar_latin1(texto):
    """Garante que o texto cabe no banco latin1 (sem emoji/travessão longo)."""
    try:
        texto.encode("latin-1")
        return True
    except UnicodeEncodeError:
        return False
