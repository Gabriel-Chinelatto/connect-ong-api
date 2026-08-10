# Connect ONG — Como o projeto foi desenvolvido

Este documento descreve como a plataforma Connect ONG foi construída: propósito, equipe, arquitetura, tecnologias, COMO CADA FUNCIONALIDADE FOI IMPLEMENTADA, decisões de projeto, segurança, uso de Inteligência Artificial, hospedagem e histórico de versões. Use-o para responder qualquer pergunta sobre o DESENVOLVIMENTO do projeto (o que foi feito, como funciona por dentro, com qual tecnologia e por quê).

## O que é o Connect ONG
O Connect ONG é uma plataforma que conecta doadores a ONGs (organizações sociais) com transparência e tecnologia. A missão é facilitar doações e conectar a generosidade a quem realmente precisa. Existem dois papéis principais: o DOADOR (encontra necessidades, demonstra interesse, conversa com a ONG e doa itens ou dinheiro) e a ONG (publica necessidades e campanhas, aceita interessados, presta contas). É um Projeto Integrador do curso de Desenvolvimento de Sistemas do COTIL (Colégio Técnico de Limeira) / UNICAMP, feito para a feira de ciências e tecnologia FECITEC.

## Equipe (4º DSN — COTIL/UNICAMP)
- Gabriel Chinelatto — Back-end e Designer.
- Arthur Souza — Designer e Tester.
- Luan Felipe — Back-end e Designer.
- Abner Viola — Front-end.
Cada integrante contribui com commits no GitHub, atendendo às regras de entrega do projeto.

## Arquitetura geral (3 frontends distintos + 1 backend + banco)
A regra de entrega exige TRÊS frontends em tecnologias distintas. O Connect ONG tem:
1. App MOBILE (experiência do DOADOR) — Flutter (Dart). É o app pessoal do doador.
2. App DESKTOP (painel administrativo da ONG) — Flutter (Dart). É onde a ONG gerencia necessidades, campanhas, interessados e prestações de contas.
3. WEB (experiência do DOADOR no navegador) — HTML, CSS e JavaScript puro (sem framework), em repositório separado.
Todos consomem o mesmo BACKEND: uma API REST em Java com Spring Boot, conectada a um banco MySQL. São 4 repositórios no GitHub (backend, mobile, desktop, web), todos públicos.

## Como o BACKEND está organizado (camadas e pacotes)
O código segue arquitetura em camadas, com responsabilidade única por classe:
- `controller/` — recebe a requisição HTTP, valida a entrada e devolve a resposta. Um controller por recurso (ex.: `ONGController`, `NecessidadeController`, `InteresseController`, `MensagemController`, `DoacaoController`, `PrestacaoController`, `UsuarioController`, `AuthController`).
- `service/` — regra de negócio (ex.: `ONGService`, `InteresseService`, `TransparenciaService`, `FreteService`, `AssistenteService`).
- `repository/` — acesso ao banco com Spring Data JPA (interfaces que geram as consultas).
- `dto/` — objetos de entrada e saída. As entidades NÃO são expostas diretamente; DTOs controlam o que entra e o que sai (privacidade e contrato estável).
- `model/` — entidades JPA mapeadas para as tabelas (Usuario, Ong, Necessidade, Interesse, Mensagem, Doacao, DoacaoFinanceira, Campanha, Prestacao, Avaliacao, Notificacao, Favorito, Bloqueio, Denuncia, Atividade, AuditLog, Preferencia, entre outras).
- `security/` — filtro JWT, identidade do usuário autenticado e helpers de autorização.
- `exception/` — tratamento centralizado de erros com `@RestControllerAdvice`: o cliente recebe um JSON `{"erro": "..."}` em vez de stack trace.
- `config/` — configuração de segurança, CORS e afins.
A documentação da API é gerada por OpenAPI/Swagger.

## Principais endpoints da API (agrupados por recurso)
- Autenticação e conta: `POST /usuarios/login`, `POST /usuarios/login-2fa`, `POST /usuarios/registro`, `POST /auth/refresh`, `POST /usuarios/esqueci-senha`, `POST /usuarios/redefinir-senha`, `PUT /usuarios/{id}/senha`, `PUT /usuarios/{id}/email`, `GET/PUT /usuarios/{id}/preferencias`, `DELETE /usuarios/{id}`.
- ONGs: `GET /ongs`, `GET /ongs/{id}`, `PUT /ongs/{id}`, `POST /ongs/registro`, `GET /ongs/{id}/perfil-publico`, `GET /ongs/{id}/transparencia`, `PUT /ongs/{id}/verificar`, `GET /ongs/ranking`, `GET /ongs/destaques`.
- Necessidades: `GET /necessidades` (com busca e filtros), `POST /necessidades`, `PUT /necessidades/{id}`, `DELETE /necessidades/{id}`.
- Match: `POST /interesses` (o doador demonstra interesse), `PUT /interesses/{id}/aceitar`, `PUT /interesses/{id}/recusar`, `PUT /interesses/{id}/concluir`, `GET /interesses/minhas`, `GET /interesses/ong/{ongId}`.
- Chat: `GET /mensagens/...`, `POST /mensagens`, `POST /mensagens/digitando`.
- Campanhas e PIX: `GET /campanhas`, `POST /campanhas`, `POST /campanhas/{id}/contribuir`, `PUT /campanhas/{id}/encerrar`, `GET /doacoes-financeiras/...`.
- Prestação de contas: `GET/POST /prestacoes`, `GET /prestacoes/pendencias`.
- Social: `/avaliacoes`, `/avaliacoes-doador`, `/favoritos`, `/conquistas`, `/atividades`, `/notificacoes` (com `PUT /notificacoes/{id}/lida` e `PUT /notificacoes/marcar-todas`), `/bloqueios`, `/denuncias`.
- IA: `POST /assistente` (Dora, para doadores), `POST /assistente-dev` (este assistente, público), `POST /ia/sobre-ong`, `POST /ia/redacao`, `POST /ia/resumo-impacto`, `POST /ia/sugestoes`.
- Público (sem login): `GET /publico/estatisticas`, `GET /categorias`, `POST /frete/estimar`.

## Como funciona o MATCH (o coração do produto)
1. A ONG publica uma NECESSIDADE (título, categoria, quantidade, urgência).
2. O doador encontra a necessidade e cria um INTERESSE (`POST /interesses`). O interesse nasce com status `PENDENTE`.
3. A ONG vê o interessado no painel e decide: `ACEITO` ou `RECUSADO`. Quando aceita, vira um "match" e o CHAT entre as duas partes é liberado.
4. Quando a doação é entregue, a ONG marca como `CONCLUIDO`, e aí entram as avaliações (a ONG avalia o doador e o doador avalia a ONG).
A entidade `Interesse` guarda também a data de cada mudança de status (`dataStatus`), para o painel mostrar "aceito em...", "recusado em...". Se a ONG recusa, o doador é notificado e a necessidade reabre com a opção "Demonstrar novamente". Um agendador (`EsperaMatchScheduler`) calcula quantos dias o interesse está aguardando, e o app mostra isso na aba "Aguardando".

## Autenticação e autorização (como a API protege os dados)
- Login devolve um **access token JWT** válido por **12 horas** e um **refresh token** válido por **7 dias** (`POST /auth/refresh` renova). O token é assinado com HS512 e a chave vem de variável de ambiente (nunca versionada).
- As claims do token carregam id, nome, tipo (DOADOR/ONG/ADMIN) e `ongId` quando é uma ONG.
- Um filtro (`JwtAuthFilter`) valida o token em toda requisição protegida e coloca a identidade no contexto do Spring Security.
- AUTORIZAÇÃO por dono (evita IDOR): a classe `SecurityUtils` oferece `exigirUsuario(id)` e `exigirOng(ongId)`, que comparam o id do recurso com a identidade DO TOKEN — nunca com um id enviado pelo cliente. Quem tenta acessar dado alheio recebe 403.
- Sessão expirada: se qualquer chamada autenticada recebe 401, os apps deslogam e voltam ao login automaticamente, em vez de travar a tela.
- 2FA opcional no login (`POST /usuarios/login-2fa`) e alteração de e-mail exigindo a senha atual.

## Rate limiting (proteção contra abuso)
`RateLimitService` conta tentativas por origem real de acesso (respeitando o cabeçalho de proxy) numa janela de **15 minutos**, com limite padrão de **5 tentativas**. Vale para login, cadastro, recuperação de senha, contribuição via PIX e os endpoints de IA (que têm limite próprio). Excedido, a API responde **429**.

## Banco de dados e migrações
MySQL. O esquema é versionado com **Liquibase** (58 changesets no `db.changelog-master.yaml`), e o Hibernate roda em `ddl-auto=validate` — ou seja, o Hibernate NÃO altera mais o banco; ele apenas confere se as entidades batem com o esquema. Foi escolhido Liquibase e não Flyway porque a versão Community do Flyway deixou de suportar o MySQL 5.6 da escola (exigiria a edição paga). Os changesets são aditivos (criam tabelas, colunas e índices) para nunca perder dado existente.

## Performance: a regra dos 600 ms
O backend roda no Render (Oregon, EUA) e o MySQL fica na escola (Brasil). **Cada ida ao banco custa ~600 ms** — o SQL em si roda em 0,025 s; o custo é a viagem. Portanto o tempo de um endpoint ≈ (nº de consultas) × 600 ms. Duas regras nasceram disso:
1. Nunca fazer consulta dentro de laço (problema N+1).
2. `@ManyToOne` é EAGER por padrão no JPA, então um `findAll()` dispara uma consulta por relação por item. A correção padrão é `LEFT JOIN FETCH` numa `@Query`.
Resultados medidos: `/publico/estatisticas` caiu de 4,8 s para 0,50 s; `/necessidades` de 3,9 s para 0,77 s; `/ongs` de 6,9 s para 1,85 s.

## Score de transparência e o "foguinho" (streak)
`TransparenciaService` calcula uma nota de 0 a 100: +25 se a ONG é verificada, até +25 pela nota média das avaliações, +5 por prestação de contas (até 5), +5 por campanha concluída (até 5) e **−5 por pendência** (campanha concluída há mais de 10 dias sem prestação de contas). O score vira um nível: OURO (≥75), PRATA, BRONZE. A ONG que está em 1º lugar acumula dias no topo, exibidos como um chip de "foguinho" 🔥 (estilo streak do TikTok) no perfil, no ranking e nos destaques.

## Doação em dinheiro (PIX) em 2 fases
A contribuição para uma campanha acontece em duas etapas: primeiro o doador confirma a intenção e recebe o código PIX (simulado, já que não há integração bancária real num projeto escolar); depois confirma que pagou, e aí a doação entra na contabilidade da campanha. O endpoint é `POST /campanhas/{id}/contribuir`, com rate limiting e o nome do doador vindo do token (não do corpo da requisição, para não ser forjado).

## Inteligência Artificial no projeto
- A assistente de doações se chama **Dora**. Ela sugere o que doar, encontra ONGs perto do doador, tira dúvidas e analisa a FOTO de um item para identificar categoria e estimar peso.
- Provedor: **API da Groq** (modelos de linguagem gratuitos). O modelo de texto padrão é o `llama-3.1-8b-instant` e o de visão é um modelo Llama 4 Scout. A URL e os modelos são configuráveis por propriedade.
- A chave da API fica SEMPRE no backend (nunca no aplicativo). O backend injeta dados reais como contexto — técnica de **grounding** — para as respostas serem fiéis ao que existe no banco.
- Cada tarefa usa uma **temperatura** diferente (mais baixa para tarefas factuais, mais alta para redação) e um limite de tokens próprio, para controlar custo e alucinação.
- Há um modo de reserva (**fallback por regras**): se a IA estiver indisponível, sem chave ou no limite de uso, o sistema responde por regras simples — nunca deixa o usuário sem resposta. Quando isso acontece, a resposta vem marcada como "Modo básico".
- Usos da IA: Dora (chat do doador), análise de foto do item, estimativa de peso/categoria no frete, redação de necessidades, texto "Sobre" da ONG (com loop de refino), resumo de impacto da ONG, sugestões personalizadas e este assistente "Sobre o Desenvolvimento".

## Simulador de frete (como é calculado)
`FreteService` estima o custo de enviar a doação: o PESO vem informado pelo doador ou é estimado pela IA a partir do item e da quantidade; a DISTÂNCIA sai de uma base **offline** de municípios (coordenadas do IBGE embutidas no app), calculada pela fórmula de **Haversine** entre as duas cidades. O resultado é apresentado como estimativa, com aviso explícito de que não é cotação oficial de transportadora.

## Endereço da ONG e mapa por coordenada
No painel da ONG, o campo de endereço tem autocomplete usando o **Nominatim (OpenStreetMap)** — serviço gratuito e sem chave. Ao escolher uma sugestão, o sistema guarda latitude e longitude, o que garante que o endereço existe e faz o mapa da web e o link do Google Maps apontarem o local EXATO. Sem coordenada, o mapa cai no centro da cidade. A busca é ancorada na cidade/UF já preenchidas no formulário, para não trazer ruas homônimas de outros estados.

## Os aplicativos Flutter (mobile e desktop) por dentro
- Organização: `screens/` (telas), `services/` (uma classe por recurso da API), `models/` (objetos do domínio), `widgets/` (componentes reutilizáveis), `theme/` (design system: cores, espaçamentos, raios) e `config/` (preferências).
- Design system: cores, espaçamentos e raios centralizados, em vez de valores soltos pelas telas.
- Estado das telas: `StatefulWidget` com estados explícitos de carregando/erro/vazio — toda tela mostra feedback visual.
- Acessibilidade: alto contraste real, tamanho de fonte ajustável, fonte para dislexia (Lexend) e navegação simplificada (transições curtas).
- Resiliência de rede: o timeout é ADAPTATIVO (espera até 100 s enquanto o servidor pode estar hibernando e 12 s depois que ele responde) e as LEITURAS se repetem automaticamente até 2 vezes quando o servidor devolve 502/503/504 ou a rede falha. Escritas (POST/PUT/DELETE) não se repetem, para não duplicar uma doação.

## A WEB (HTML/CSS/JS puro) por dentro
Arquivos: `index.html` (marcação), `css/styles.css` (estilos), `js/api.js` (camada REST + sessão JWT no localStorage), `js/ui.js` (helpers) e `js/app.js` (roteador SPA e telas). Não há framework nem build: o navegador carrega os arquivos direto. O Tailwind entra por CDN. Para evitar CORS em produção, o Netlify faz proxy da API no mesmo endereço do site (mesma origem).

### Por que a web foi feita em HTML/CSS/JS puro (e não em Flutter Web)
Decisão consciente: como mobile e desktop já são Flutter, fazer a web em JavaScript puro conta como uma terceira tecnologia realmente distinta (atende à regra de 3 frontends distintos), dá mais liberdade visual e é mais fácil de manter. O custo aceito foi reconstruir as funções em JavaScript, viável porque a API já estava pronta.

## Recursos exclusivos da WEB (o que só existe no navegador)
- Mapa interativo de ONGs (Leaflet + OpenStreetMap), com pins clicáveis que abrem o perfil.
- Comparador de ONGs: até 3 lado a lado (nota, transparência, prestações) com link compartilhável.
- Modo Quiosque / Apresentação: tela cheia para o estande da feira, com números animados e letreiro ao vivo.
- Relatório de impacto imprimível / em PDF.
- Busca rápida com atalho de teclado (Ctrl/Cmd + K).
- Voz na Dora, cartão de impacto compartilhável, QR Code do perfil da ONG e instalação como aplicativo (PWA).

## Hospedagem e publicação (tudo automático pelo Git)
- BACKEND: **Render** (plano gratuito, Docker). Cada push na branch `master` dispara o deploy.
- WEB: **Netlify**, com proxy da API na mesma origem.
- APP DO DOADOR e PAINEL DA ONG: publicados como site estático no **GitHub Pages** por GitHub Actions, que roda `flutter analyze` + testes e só publica se tudo passar.
- Limitações conhecidas do plano gratuito do Render: o serviço **hiberna após ~15 minutos** sem acesso (a primeira chamada seguinte pode levar de 10 a 95 segundos) e tem apenas **512 MB de memória**. O estouro desses 512 MB chegou a derrubar a API; a correção foi limitar a JVM no Dockerfile (heap máximo, coletor serial, metaspace limitado) e reduzir o Tomcat de 200 para 25 threads — medido: 271 MB em uso, com folga.

## Testes automatizados
- Backend: **165 testes** (JUnit + Spring Boot Test) rodando em banco H2 em memória, então a suíte não toca o banco da escola.
- App do doador (mobile): cerca de 90 testes, incluindo testes que previnem estouro de layout com fonte grande.
- Painel da ONG (desktop): dezenas de testes de regras e de rede.
- Além disso, cada rodada de mudanças é verificada ao vivo contra a API real, e a interface é conferida por capturas de tela automatizadas.

## Métodos e boas práticas de desenvolvimento
- API RESTful com verbos HTTP corretos e endpoints por recurso; DTOs na entrada e na saída; validação centralizada; tratamento global de exceções; documentação OpenAPI/Swagger.
- Cada mudança útil vira um ponto de restauração no Git (commits frequentes e descritivos), com o histórico dividido entre os integrantes.
- Verificação empírica: nada é dado como pronto sem teste real (a suíte roda verde a cada rodada).
- Dados brasileiros offline: estados e municípios do IBGE embutidos, sem depender de internet.
- "Modo Feira": um ajuste local que exibe as credenciais de demonstração na tela de login, para o visitante do estande entrar sozinho. Fica desligado por padrão em ambientes públicos.

## Histórico de versões (o que cada versão entregou)
- v1.0 — Fundação & Match: cadastro de doadores e ONGs, publicação de necessidades, o match (interesse + aceite) e o chat.
- v1.1 — Confiança & Transparência: verificação da ONG (selo), prestação de contas, avaliações e central de notificações.
- v1.2 — Engajamento & Doações: feed com busca e filtros, campanhas com PIX, timeline, mural, ranking, conquistas e favoritos.
- v1.3 — Segurança & Conformidade: JWT e autorização por dono, LGPD e papel de administrador, exclusão segura de conta, "esqueci a senha" com limite de tentativas.
- v1.4 — Experiência renovada: redesenho do app do doador (5 abas), matches em 3 abas, perfil público do doador (avaliação estilo Uber), PIX em 2 fases, streak no ranking e chat estilo WhatsApp.
- v1.5 — Comunidade & Controle: bloqueio de doador, estado e cidade pelo IBGE offline, alto contraste, navegação simplificada e "como chegar" no Google Maps.
- v1.6 — Tempo real & Segurança extra: matches e interesses em tempo real, 2FA no login e alteração de e-mail com confirmação de senha.
- v1.7 — Assistente com IA: a Dora, análise de foto que identifica o item e sugere ONGs, e histórico de conversas.
- v1.8 — Revisão final de segurança: sessão protegida, privacidade real e proteção contra abuso.
- v1.9 — Frete inteligente e mais IA: simulador de frete (distância + peso), IA que estima peso e categoria, resumo de impacto da ONG e sugestões personalizadas.
- v2.0 — Recursos exclusivos da web: mapa interativo, comparador de ONGs, Modo Quiosque, relatório imprimível e busca rápida (Ctrl/Cmd + K).
- v2.1 — Voz, cartão de impacto, QR Code do perfil e instalação como aplicativo (PWA).
- v2.2 — Polimento e acessibilidade: portal institucional com caminho único de login, documentos legais redesenhados, correções de contraste no tema escuro e ganhos de desempenho.

## Marcos e eventos
O projeto foi preparado para a FECITEC (feira de ciências e tecnologia). Há "Modo Feira/Quiosque" e contas de demonstração para apresentações. A entrega final do curso ocorre no fim do ano.
