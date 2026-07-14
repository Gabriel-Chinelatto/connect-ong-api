# Connect ONG — Como o projeto foi desenvolvido

Este documento descreve como a plataforma Connect ONG foi construída: propósito, equipe, arquitetura, tecnologias, métodos de trabalho, decisões de projeto, segurança, uso de Inteligência Artificial e o histórico de versões. Use-o para responder perguntas sobre o DESENVOLVIMENTO do projeto (o que foi feito, quando, com qual tecnologia e por quê).

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
1. App MOBILE (experiência do DOADOR) — feito em Flutter (Dart). É o app pessoal do doador.
2. App DESKTOP (painel administrativo da ONG) — feito em Flutter (Dart). É onde a ONG gerencia necessidades, campanhas, interessados e prestações de contas.
3. WEB (experiência do DOADOR no navegador) — feita em HTML, CSS e JavaScript puro (sem framework), num repositório separado.
Todos consomem o mesmo BACKEND: uma API REST em Java com Spring Boot, conectada a um banco de dados MySQL.

## Por que a web foi feita em HTML/CSS/JS puro (e não em Flutter Web)
Decisão consciente: como mobile e desktop já são Flutter, fazer a web em HTML/CSS/JavaScript puro conta como uma terceira tecnologia realmente distinta (atende à regra de 3 frontends distintos e impressiona mais a banca), dá mais liberdade visual e é mais fácil de manter pela equipe. O custo aceito foi reconstruir as funções em JavaScript, o que foi viável porque a API já estava pronta e o CORS libera qualquer origem localhost. A web foi organizada em arquivos separados: index.html (marcação), css/styles.css (estilos), js/api.js (camada REST + sessão JWT), js/ui.js (helpers) e js/app.js (roteador SPA e telas).

## Tecnologias (stack)
- Backend: Java + Spring Boot (Spring Web, Spring Security, Spring Data JPA/Hibernate), autenticação com JWT.
- Banco de dados: MySQL. Migrações de esquema versionadas (changesets), aplicadas no banco real.
- Mobile e Desktop: Flutter (Dart), consumindo a API por HTTP.
- Web: HTML5, CSS3 e JavaScript puro; Tailwind CSS (via CDN) para estilo; fontes Google (Inter, Montserrat, Permanent Marker, Lexend) e ícones Phosphor; Leaflet + OpenStreetMap para o mapa de ONGs (recurso exclusivo da web).
- Inteligência Artificial: API da Groq (modelos de linguagem gratuitos) para o assistente de doações, com a chave protegida no backend.
- Identidade visual: verde #008542 e laranja #ff7b00.

## Métodos e boas práticas de desenvolvimento
- API RESTful com verbos HTTP corretos e endpoints por recurso.
- Segurança levada a sério: senhas com hash, JWT para sessão, autorização por dono do recurso (evita IDOR), rate limiting em login/cadastro/contribuição, conformidade com a LGPD e papel de administrador.
- Cada mudança útil vira um ponto de restauração no Git (commits frequentes), com o histórico dividido entre os integrantes.
- Verificação empírica: as funções foram testadas ao vivo contra a API real (e o backend tem uma suíte de testes automatizados que passou verde a cada rodada).
- Acessibilidade: alto contraste, tamanho de fonte ajustável, fonte para dislexia e navegação simplificada.
- Dados brasileiros offline: estados e municípios do IBGE embutidos para cadastro e para o cálculo de frete, sem depender de internet.

## Inteligência Artificial no projeto
- A assistente de doações se chama "Dora". Ela sugere o que doar, encontra ONGs perto do doador, tira dúvidas e até analisa a foto de um item para identificar a categoria.
- A IA usa a API da Groq. A chave da API fica SEMPRE no backend (nunca exposta no aplicativo), e o backend injeta dados reais como contexto ("grounding") para as respostas serem fiéis.
- Há um modo de reserva ("fallback") por regras: se a IA estiver indisponível ou sem chave, o sistema responde com base em regras simples, para nunca deixar o usuário sem resposta.
- A IA também é usada para: estimar o peso de uma doação e a categoria no simulador de frete, escrever um resumo de impacto da ONG, e melhorar o texto de necessidades.

## Segurança (resumo)
Login com JWT; sessão que volta ao login quando expira; autorização por dono (um usuário só acessa os próprios dados); privacidade real (telefone e e-mail só aparecem quando o usuário permite); proteção contra abuso (rate limiting) em login, cadastro, redefinição de senha e contribuições; exclusão de conta em conformidade com a LGPD; verificação em duas etapas (2FA) opcional no login. Houve uma revisão final de segurança que fechou os achados de código.

## Recursos exclusivos da WEB (o que só existe no navegador)
Para a web não ser uma cópia do mobile, foram criados recursos que só fazem sentido em tela larga:
- Mapa interativo de ONGs (Leaflet + OpenStreetMap): as ONGs aparecem no mapa por cidade, com pins clicáveis que abrem o perfil. A localização por cidade é resolvida offline (uma tabela de coordenadas embutida).
- Comparador de ONGs: coloca até 3 ONGs lado a lado (nota, transparência, prestações, necessidades, campanhas) e gera um link compartilhável da comparação.
- Modo Quiosque / Apresentação: uma tela cheia para o estande da feira, com números animados, ranking de transparência e um letreiro de atividades ao vivo.
- Relatório de impacto imprimível / em PDF: gera um relatório do doador (doações concluídas, doações em dinheiro e conquistas) pronto para imprimir.
- Busca rápida com atalho de teclado (Ctrl/Cmd + K): navega por telas, ONGs, necessidades e ações — um recurso de usuário avançado típico de web.

## Histórico de versões (o que cada versão entregou)
- v1.0 — Fundação & Match: cadastro de doadores e ONGs, publicação de necessidades, o "match" (interesse do doador e aceite da ONG) e o chat entre as partes.
- v1.1 — Confiança & Transparência: verificação da ONG (selo), prestação de contas, avaliações das ONGs e central de notificações.
- v1.2 — Engajamento & Doações: feed com busca e filtros, campanhas com doação via PIX, e as telas de timeline, mural, ranking, conquistas e favoritos.
- v1.3 — Segurança & Conformidade: login com JWT e autorização por dono, LGPD e papel de administrador, exclusão segura de conta, e "esqueci a senha" com limite de tentativas.
- v1.4 — Experiência renovada: redesenho do app do doador (5 abas), matches em 3 abas, perfil público do doador (avaliação estilo Uber), PIX em 2 fases, streak no ranking e chat estilo WhatsApp.
- v1.5 — Comunidade & Controle: bloqueio de doador (estilo WhatsApp), estado e cidade pelo IBGE offline, alto contraste e navegação simplificada, e "como chegar" no Google Maps.
- v1.6 — Tempo real & Segurança extra: matches e interesses em tempo real, 2FA no login e alteração de e-mail com confirmação de senha.
- v1.7 — Assistente com IA: a Dora (assistente de doação com IA), análise de foto que identifica o item e sugere ONGs, e histórico de conversas estilo ChatGPT.
- v1.8 — Revisão final de segurança: sessão protegida, privacidade real (telefone/e-mail sob permissão) e proteção contra abuso em contribuições, cadastro e senha.
- v1.9 — Frete inteligente e mais IA: navegação mais fluida, simulador de frete no perfil da ONG (distância + peso), IA que estima o peso e avisa se a categoria não combina, resumo de impacto da ONG escrito por IA e "sugestões para você" por perfil e cidade.
- v2.0 — Recursos exclusivos da web: mapa interativo de ONGs, comparador de ONGs lado a lado com link compartilhável, Modo Quiosque em tela cheia para eventos, relatório de impacto imprimível/PDF e busca rápida com atalho de teclado (Ctrl/Cmd + K).

## Marcos e eventos
O projeto foi preparado para a FECITEC (feira de ciências e tecnologia). O aplicativo tem um "Modo Feira/Quiosque" e contas de demonstração para apresentações. A entrega final do curso ocorre no fim do ano.
