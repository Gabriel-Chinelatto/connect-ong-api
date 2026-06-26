# Arquitetura — Connect ONG API

Documentação interna da API REST do **Connect ONG**, a plataforma que conecta
**doadores** a **ONGs**. Este documento descreve a visão geral do sistema, as
camadas do backend e o fluxo de uma requisição.

## Visão geral do sistema

O Connect ONG é composto por:

- **App mobile (Flutter)** — o app do **doador**.
- **App desktop (Flutter)** — o painel administrativo da **ONG**.
- **Web** — frontend web.
- **Esta API (Spring Boot)** — núcleo de regras de negócio e persistência.
- **MySQL remoto** — banco de dados hospedado na escola.

Os três frontends consomem a mesma API via HTTP/JSON, autenticando-se com JWT
(`Authorization: Bearer <token>`). A API é *stateless*: nenhuma sessão é
guardada no servidor; cada requisição carrega seu próprio token.

### Funcionalidade central ("hero feature")

O fluxo que dá sentido à plataforma é o **match**:

1. A ONG publica uma **Necessidade** (o que precisa receber).
2. O doador demonstra **Interesse** naquela necessidade (status `PENDENTE`).
3. A ONG **aceita** o interesse (status `ACEITO`) — isso é o **match**.
4. O match **habilita o chat** (`Mensagem`) entre doador e ONG.

A partir do match também são possíveis a prestação de contas, avaliação e a
doação financeira.

## Camadas

A API segue a separação clássica em camadas, do mais externo ao mais interno:

```
HTTP  →  Controller  →  Service  →  Repository  →  Entity (JPA)  →  MySQL
                  ↑ DTO ↓                                    ↑ Liquibase (schema)
                  └──────────── GlobalExceptionHandler ──────────────┘
```

- **Controller** (`controller/`) — expõe os recursos REST (rotas, verbos HTTP,
  serialização). Não contém regra de negócio; valida a entrada e delega ao
  service. Cada controller documenta no cabeçalho o path base e se é público ou
  exige autenticação/ownership.

- **Service** (`service/`) — concentra a **regra de negócio**: validações de
  domínio, transições de estado (ex.: `PENDENTE → ACEITO`), checagens de
  ownership (via `SecurityUtils`) e orquestração de vários repositórios. É onde
  vivem as decisões ("chat só após match `ACEITO`", "score de transparência",
  etc.).

- **Repository** (`repository/`) — interfaces Spring Data JPA. Abstraem o
  acesso ao banco com *query methods* (`findBy...`) e, quando necessário,
  consultas agregadas (`@Query` com `GROUP BY`) para evitar N+1.

- **Entity / model** (`model/`) — entidades JPA mapeadas para as tabelas. Além
  do mapeamento, carregam regras implícitas do domínio em comentários (ex.: o
  hash de senha é WRITE_ONLY; o chat só existe sobre um `Interesse` aceito).

- **DTO** (`dto/`) — objetos de transporte que isolam a representação da API das
  entidades internas. Garantem, entre outras coisas, que dados sensíveis (hash
  de senha, valores privados de doação) não vazem na resposta.

- **GlobalExceptionHandler** (`exception/`) — traduz exceções de domínio em
  respostas HTTP consistentes (ex.: `AcessoNegadoException → 403`, validação →
  `400`), de forma centralizada, sem `try/catch` espalhado pelos controllers.

- **Security / Config** (`security/`, `config/`) — `SecurityFilterChain`,
  `JwtAuthFilter`, helpers de ownership e configuração de CORS/OpenAPI. Ver
  [SEGURANCA.md](SEGURANCA.md).

## Fluxo de uma requisição

Exemplo: doador envia uma mensagem no chat de um match.

1. O cliente faz `POST /mensagens` com `Authorization: Bearer <token>` e o corpo
   JSON (DTO de requisição).
2. O **`JwtAuthFilter`** intercepta, valida o token e coloca o
   `UsuarioAutenticado` no contexto de segurança. Sem token válido em rota
   protegida → **401**.
3. O **`MensagemController`** recebe a requisição já autenticada e chama o
   service.
4. O **`MensagemService`** aplica a regra de negócio: confirma que existe um
   `Interesse` com status `ACEITO`, que o usuário do token **participa** daquele
   match (senão `AcessoNegadoException → 403`), grava a `Mensagem` e dispara a
   notificação para o outro lado.
5. O **`MensagemRepository`** persiste a entidade no MySQL.
6. O resultado volta como **DTO de resposta** (JSON), sem expor dados internos.
7. Se qualquer exceção for lançada, o **`GlobalExceptionHandler`** a converte no
   status HTTP adequado.

## Persistência e schema

- O Hibernate roda com `ddl-auto=validate`: **não altera** o banco, apenas valida
  que as entidades batem com a schema.
- A **schema e os índices** são versionados pelo **Liquibase**
  (`db/changelog/db.changelog-master.yaml`).
- Detalhes do modelo de dados e LGPD em [DADOS.md](DADOS.md); normalização em
  [NORMALIZACAO.md](NORMALIZACAO.md).
