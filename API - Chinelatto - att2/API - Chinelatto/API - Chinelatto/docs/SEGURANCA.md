# Segurança — Connect ONG API

Modelo de autenticação e autorização da API. O objetivo é simples: **provar quem
é o usuário** (autenticação, via JWT) e **garantir que ele só mexe nos próprios
dados** (autorização por dono).

## Autenticação (JWT)

- O login/cadastro (`POST /usuarios/login`, `POST /usuarios`,
  `POST /ongs/registro`) retornam um **accessToken** e um **refreshToken**.
- Os apps enviam o access token em toda requisição protegida no header
  `Authorization: Bearer <token>`.
- Tempos de vida (em `JwtService`):
  - **access token: 12h** — cobre uma sessão longa (ex.: um dia de feira) sem
    forçar refresh.
  - **refresh token: 7 dias** — usado para obter um novo access token sem novo
    login.
- A API é **stateless** (`SessionCreationPolicy.STATELESS`): não há sessão no
  servidor; a identidade vem inteiramente do token.

### Segredo de assinatura

- A chave HMAC vem da propriedade **`app.jwt.secret`** (idealmente da variável de
  ambiente `APP_JWT_SECRET` em produção).
- Existe um default apenas para o ambiente de desenvolvimento não quebrar. **Em
  produção, defina um segredo novo por variável de ambiente** — o valor de
  desenvolvimento já vazou no histórico do Git e não deve ser usado.

## Spring Security

A cadeia de filtros é configurada em `config/SecurityConfig.java`
(`SecurityFilterChain`):

- `csrf` desabilitado (API stateless, sem cookies de sessão).
- `httpBasic` e `formLogin` desabilitados (a autenticação é só por JWT).
- **`JwtAuthFilter`** roda antes do `UsernamePasswordAuthenticationFilter`:
  valida o Bearer token e popula o contexto com o `UsuarioAutenticado`.
- Sem token em rota protegida → **401** (entry point customizado), e não o 403
  padrão — distinguindo "não autenticado" de "autenticado sem permissão".

### Whitelist pública

Endpoints liberados sem token:

- `/auth/**`
- `/publico/**` (vitrine: estatísticas, ranking de transparência)
- Swagger: `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
- `/error`
- `POST /usuarios` e `POST /usuarios/login` (cadastro/login de usuário)
- `POST /ongs/registro` (registro de ONG)

**Todo o resto exige autenticação** (`anyRequest().authenticated()`).

### Toggle `app.security.enforce`

Válvula de escape para demonstração/feira: a propriedade
**`app.security.enforce`** (default `true`) pode ser posta como `false` para
liberar tudo (`permitAll`) temporariamente, **sem recompilar**. Em produção deve
**permanecer `true`**.

## Autorização por dono (ownership)

Autenticação prova *quem* é o usuário; a **autorização** garante que ele só
acessa os **próprios** recursos. Isso é feito na camada de service usando:

- **`security/UsuarioAutenticado`** — identidade extraída do token (`id`,
  `ongId`), nunca de ids vindos do cliente.
- **`security/SecurityUtils`** — helpers que comparam o recurso com a identidade
  do token e lançam `AcessoNegadoException` (**HTTP 403**) quando a checagem
  falha:
  - `exigirUsuario(idAlvo)` — o id alvo precisa ser o do próprio usuário.
  - `exigirOng(ongIdAlvo)` — o ongId alvo precisa ser o da ONG do usuário.
  - `exigirUsuarioOuOng(...)` — dono por um dos dois lados; usado no **chat**
    (tanto o doador quanto a ONG do match podem ler/escrever).

A regra de ouro: **a identidade vem sempre do token**, jamais de um id enviado
pelo cliente — isso impede que alguém acesse dados de outro passando o id alheio.

## Senha

- A senha do usuário é guardada como **hash BCrypt** (`BCryptPasswordEncoder`),
  nunca em texto puro.
- Na entidade `Usuario`, o campo `senha` é **WRITE_ONLY** no JSON
  (`@JsonProperty(access = WRITE_ONLY)`): pode entrar na desserialização, mas
  **nunca sai** em uma resposta — o hash não vaza nem por associação.
- O login compara a senha enviada com o hash via BCrypt e responde com **401
  genérico** em caso de falha (não revela se o e-mail existe — anti-enumeração).

## CORS

- Configurado em `SecurityConfig` (`CorsConfigurationSource`).
- **Não usa `*`**: aceita apenas as origens da propriedade
  `app.cors.allowed-origins` (default cobre `localhost`/`127.0.0.1` para
  desenvolvimento dos apps Flutter web/desktop e do Swagger).
- Métodos permitidos: `GET, POST, PUT, DELETE, PATCH, OPTIONS`;
  `allowCredentials = true`.

## Resumo dos status HTTP de segurança

| Situação                                            | Status |
|-----------------------------------------------------|--------|
| Sem token (ou inválido) em rota protegida           | 401    |
| Autenticado, mas acessando dado de outro            | 403    |
| Login com credenciais inválidas                     | 401    |
