# Revisão de normalização — Connect ONG (Bloco 17)

Revisão do modelo de dados ao migrar para schema versionada (Liquibase) e
`ddl-auto=validate`. Objetivo: registrar o estado de normalização, justificar
as denormalizações intencionais e listar pendências.

## Estado geral

O schema está, no geral, na **3FN**: cada entidade tem chave primária própria
(`id` auto-incremento), e os relacionamentos usam colunas de id
(`ong_id`, `doador_id`, `necessidade_id`, `interesse_id`, `usuario_id`).

## Denormalizações intencionais (snapshots)

Algumas colunas guardam uma **cópia do nome** no momento do registro, de
propósito, e **não** são uma violação a corrigir:

- `doacao_financeira.doador_nome`, `doacao_financeira.ong_nome`
- `avaliacao.doador_nome`

Motivo: são **comprovantes/registros históricos**. O nome exibido deve
refletir o que era verdade na hora da doação/avaliação, mesmo que o cadastro
mude depois. É o mesmo princípio de uma nota fiscal. Mantido.

## Índices adicionados (performance)

Criados via Liquibase (`db.changelog-master.yaml`) nas colunas usadas em
consultas `findBy...` que ainda não tinham índice:

`necessidade.status`, `notificacao.usuario_id`,
`doacao_financeira.doador_id/ong_id`, `avaliacao.ong_id/doador_id`,
`audit_log.usuario_id/acao`, `usuario.ong_id`.

Colunas de FK já indexadas pelo Hibernate e o `unique` de `usuario.email`
foram deixadas de fora para não duplicar índices.

## Pendências / observações

- **Tabelas legadas `doacao` e `projeto`**: existem no banco (de versões
  iniciais) mas não fazem mais parte do fluxo atual. Avaliar remoção em uma
  migration futura (depois de confirmar que nenhum dado precisa ser
  preservado).
- **`usuario.ong_id` como vínculo**: a conta de login de uma ONG referencia o
  perfil por `ong_id`. Funciona bem; uma FK formal pode ser adicionada numa
  migration futura se quisermos integridade referencial no nível do banco.
- **Baseline completo**: as tabelas existentes não estão descritas no
  changelog (predatam o Liquibase). Para reprodutir o schema do zero, gerar um
  baseline com `liquibase generateChangeLog` e versioná-lo.
