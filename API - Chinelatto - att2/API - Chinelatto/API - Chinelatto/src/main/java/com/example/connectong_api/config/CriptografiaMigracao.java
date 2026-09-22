package com.example.connectong_api.config;

import com.example.connectong_api.security.Criptografia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Cifra, no startup, os dados sensiveis que ainda estao em texto puro no banco
 * (gravados antes da criptografia existir, ou inseridos direto por SQL — como o
 * dump da demonstracao que o RESTAURAR-DEMO reimporta).
 *
 * <p>Idempotente: so toca linhas SEM o prefixo {@code enc:v1:}. Depois da
 * primeira vez, cada startup custa uma consulta vazia por coluna. Vai em lotes
 * para nao segurar memoria nem uma transacao gigante no banco da escola.
 *
 * <p><b>Trava da chave:</b> na primeira vez com APP_CRYPTO_KEY, guarda no
 * banco a impressao digital da chave (tabela {@code cripto_chave}). Dali em
 * diante, um ambiente que suba com OUTRA chave contra o mesmo banco para no
 * startup com uma mensagem clara — sem a trava, ele cifraria dados novos que
 * o resto do sistema nao consegue ler.
 *
 * <p>Desligavel com {@code app.crypto.migrar-no-startup=false}.
 */
@Component
public class CriptografiaMigracao implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CriptografiaMigracao.class);
    private static final int LOTE = 300;

    // tabela -> coluna. Devem ser as MESMAS colunas marcadas com
    // @Convert(converter = CampoCifradoConverter.class) nas entidades.
    static final List<String[]> COLUNAS = List.of(
            new String[]{"usuario", "telefone"},
            new String[]{"mensagem", "conteudo"},
            new String[]{"denuncia", "descricao"});

    private final JdbcTemplate jdbc;
    private final Criptografia criptografia;

    @Value("${app.crypto.migrar-no-startup:true}")
    private boolean ativo;

    public CriptografiaMigracao(JdbcTemplate jdbc, Criptografia criptografia) {
        this.jdbc = jdbc;
        this.criptografia = criptografia;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!criptografia.ligada()) return;
        conferirChave();
        if (!ativo) return;
        // Em segundo plano: a API ja atende enquanto o legado e cifrado (o
        // conversor le texto puro normalmente).
        Thread t = new Thread(this::migrarTudo, "cripto-migracao");
        t.setDaemon(true);
        t.start();
    }

    void migrarTudo() {
        for (String[] tc : COLUNAS) {
            try {
                int total = migrar(tc[0], tc[1]);
                if (total > 0) {
                    log.info("Criptografia: {} valor(es) de {}.{} cifrados", total, tc[0], tc[1]);
                }
            } catch (Exception e) {
                // Nunca derruba o startup: o conversor le texto puro normalmente
                // e a proxima inicializacao tenta de novo.
                log.error("Criptografia: falha ao migrar {}.{}: {}", tc[0], tc[1], e.getMessage());
            }
        }
    }

    /** Cifra os valores em texto puro de uma coluna. Devolve quantos cifrou. */
    public int migrar(String tabela, String coluna) {
        String select = "SELECT id, " + coluna + " AS v FROM " + tabela
                + " WHERE " + coluna + " IS NOT NULL AND " + coluna + " NOT LIKE 'enc:v1:%'"
                + " AND id > ? ORDER BY id LIMIT " + LOTE;
        long ultimoId = 0;
        int total = 0;
        while (true) {
            List<Map<String, Object>> linhas = jdbc.queryForList(select, ultimoId);
            if (linhas.isEmpty()) return total;
            // O lote inteiro num UNICO comando (UPDATE ... CASE id): com o banco
            // longe (~0,2 s por ida), um UPDATE por linha levava ~40 min para os
            // 13 mil registros da escola. (batchUpdate nao resolve: sem
            // rewriteBatchedStatements o driver do MySQL manda um por um.)
            StringBuilder caso = new StringBuilder();
            StringBuilder ids = new StringBuilder();
            List<Object> params = new java.util.ArrayList<>();
            List<Object> paramsIds = new java.util.ArrayList<>();
            for (Map<String, Object> l : linhas) {
                long id = ((Number) l.get("id")).longValue();
                caso.append(" WHEN ? THEN ?");
                params.add(id);
                params.add(criptografia.cifrar((String) l.get("v")));
                ids.append(ids.length() == 0 ? "?" : ",?");
                paramsIds.add(id);
                ultimoId = id;
            }
            params.addAll(paramsIds);
            // So troca o que AINDA esta em texto puro: nao sobrescreve algo que
            // outra instancia cifrou no meio do caminho (ex.: durante um deploy).
            jdbc.update("UPDATE " + tabela + " SET " + coluna + " = CASE id" + caso + " END"
                    + " WHERE id IN (" + ids + ") AND " + coluna + " NOT LIKE 'enc:v1:%'", params.toArray());
            total += linhas.size();
        }
    }

    /** Grava a impressao digital na primeira vez; depois, recusa chave diferente. */
    public void conferirChave() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS cripto_chave ("
                + "id INT PRIMARY KEY, impressao_digital VARCHAR(64) NOT NULL, criada_em DATETIME)");
        List<String> gravada = jdbc.queryForList(
                "SELECT impressao_digital FROM cripto_chave WHERE id = 1", String.class);
        if (gravada.isEmpty()) {
            jdbc.update("INSERT INTO cripto_chave (id, impressao_digital, criada_em) VALUES (1, ?, CURRENT_TIMESTAMP)",
                    criptografia.impressaoDigital());
            log.info("Criptografia: impressao digital da chave registrada no banco");
        } else if (!gravada.get(0).equals(criptografia.impressaoDigital())) {
            throw new IllegalStateException("APP_CRYPTO_KEY deste ambiente e DIFERENTE da chave que cifrou este banco. "
                    + "Use a mesma chave do servidor (ver CONNECT-ONG-SEGREDOS) — subir com outra "
                    + "deixaria mensagens e telefones ilegiveis para o resto do sistema.");
        }
    }
}
