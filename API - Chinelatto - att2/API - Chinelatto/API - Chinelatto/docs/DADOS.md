# Dados — Connect ONG API

Modelo de dados da plataforma, tratamento de dados pessoais (LGPD) e estratégia
de persistência. Para a justificativa de normalização e índices, ver
[NORMALIZACAO.md](NORMALIZACAO.md).

## Entidades principais e relações

| Entidade            | O que representa                                                        |
|---------------------|------------------------------------------------------------------------|
| **Usuario**         | Conta de acesso (doador ou admin de ONG), diferenciada por `tipo`.     |
| **Ong**             | Perfil público da ONG + sinais de confiança (selo, nota, CNPJ).        |
| **Necessidade**     | O que uma ONG precisa receber (alvo do interesse).                     |
| **Interesse**       | Interesse do doador numa necessidade; `ACEITO` = **match**.            |
| **Mensagem**        | Mensagem de chat dentro de um match (`Interesse` aceito).              |
| **Campanha**        | Campanha de arrecadação de uma ONG (meta + progresso; pode encerrar).  |
| **Prestacao**       | Prestação de contas vinculada a um match.                              |
| **DoacaoFinanceira**| Doação em dinheiro (PIX simulado) com comprovante.                     |
| **Avaliacao**       | Nota (1-5) do doador para a ONG; alimenta `notaMedia` da `Ong`.        |
| **Notificacao**     | Aviso enviado a um usuário, respeitando suas preferências.            |
| **Favorito**        | ONG/campanha favoritada por um doador.                                 |
| **AuditLog**        | Registro de auditoria de ações relevantes.                             |

Entidades auxiliares: `Atividade` (feed/timeline), `Preferencia` (preferências de
notificação por tipo), `Denuncia` (reports), `Conquista` (gamificação, derivada),
`Doacao` (catálogo legado de itens).

### Relações centrais (o fluxo do match)

```
Ong  1───*  Necessidade  1───*  Interesse  *───1  Usuario (doador)
                                    │ (status ACEITO = match)
                                    ├───*  Mensagem      (chat)
                                    └───*  Prestacao     (prestação de contas)
```

- Uma **ONG** publica várias **Necessidades**.
- Cada **Necessidade** recebe vários **Interesses** de **doadores**
  (`Usuario`). O `Interesse` evolui `PENDENTE → ACEITO | RECUSADO`.
- Sobre um `Interesse` **`ACEITO`** existem **Mensagem** (chat) e **Prestacao**.
  Regra implícita: chat e prestação **só existem após o match aceito**.

### Vínculo conta ↔ ONG

A conta de login de uma ONG é um `Usuario` com `tipo` de ONG e `ongId` apontando
para o perfil em `Ong` (doadores têm `ongId = null`). O relacionamento é por
coluna de id; ver pendências em [NORMALIZACAO.md](NORMALIZACAO.md).

### Relação `Mensagem → Interesse` (LAZY)

`Mensagem` referencia `Interesse` com **`FetchType.LAZY`** de propósito: o chat
lista muitas mensagens e carregar o `Interesse` inteiro (e a cadeia
`Necessidade → Ong` e `Doador → Usuario`) em cada uma seria desperdício — e ainda
arrastaria o hash de senha do doador. O DTO usa apenas o id.

## Tratamento de dados pessoais (LGPD)

### Dado hasheado / nunca exposto

- **`Usuario.senha`**: guardada como **hash BCrypt**, nunca em texto puro. É
  **WRITE_ONLY** no JSON — entra na desserialização, **nunca sai** na resposta.

### Público vs privado

- **Público** (a ONG quer ser encontrada): perfil da `Ong` (nome, cidade,
  descrição, selo `verificada`, `notaMedia`), `Necessidade`, ranking e
  estatísticas de transparência (endpoints `/publico/**`).
- **Privado** (exige token + ownership): dados de conta do `Usuario`, chat
  (`Mensagem`), notificações, favoritos, prestações e o detalhe das doações.
- **Doação financeira — minimização de dados**: o feed público de doações
  **não** expõe o valor nem a identidade do doador; o registro completo
  (comprovante, doador, valor) só é acessível aos donos (doador/ONG).

### Snapshots históricos (denormalização intencional)

Alguns registros guardam uma **cópia do nome** no momento do fato
(`avaliacao.doador_nome`, `doacao_financeira.doador_nome/ong_nome`). Isso é
proposital: comprovantes/registros históricos devem refletir o que era verdade
na hora, como uma nota fiscal — mesmo que o cadastro mude depois. Detalhes em
[NORMALIZACAO.md](NORMALIZACAO.md).

### Auditoria

Ações relevantes são registradas em **`AuditLog`** (ator, ação, alvo, data),
permitindo rastrear quem fez o quê. A leitura é restrita/administrativa.

## Persistência

- **Banco**: MySQL remoto da escola.
- **JPA / Hibernate** com **`ddl-auto=validate`**: o Hibernate **não altera** o
  banco; apenas valida que as entidades batem com a schema existente.
- **Schema e índices** são versionados via **Liquibase**
  (`db/changelog/db.changelog-master.yaml`). Optou-se por Liquibase (e não
  Flyway) porque o Flyway Community deixou de suportar o MySQL antigo da escola.
- **Identificadores**: cada entidade tem PK própria `id` auto-incremento
  (`GenerationType.IDENTITY`); relacionamentos por colunas de id
  (`ong_id`, `doador_id`, `necessidade_id`, `interesse_id`, `usuario_id`).
- **Datas**: várias entidades preenchem `dataCriacao`/`dataEnvio` em
  `@PrePersist`, garantindo o carimbo de tempo no momento da gravação.

O modelo está, no geral, na **3FN**, com as denormalizações de snapshot acima
documentadas como intencionais. Ver [NORMALIZACAO.md](NORMALIZACAO.md).
