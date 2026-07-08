package com.example.connectong_api.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting in-memory (sem dependencia nova), com dois modos:
 *
 * 1) FALHAS CONSECUTIVAS (login por email+IP; redefinir-senha por email):
 *    apos N falhas consecutivas dentro da janela, a chave fica bloqueada por
 *    15 minutos. Um sucesso zera o contador. Mitiga forca bruta de senha e
 *    de codigo de recuperacao.
 *
 * 2) CONTAGEM DE SOLICITACOES por IP (esqueci-senha e cadastro publico):
 *    no maximo N solicitacoes por janela de 15 minutos. Evita spam de codigo
 *    de recuperacao e cadastros em massa.
 *
 * DECISAO REGISTRADA — enumeracao de e-mail no cadastro: o cadastro publico
 * responde "Email já cadastrado", o que tecnicamente permite enumerar contas.
 * Mantivemos essa mensagem pela UX da feira (o visitante precisa entender o
 * erro na hora) e mitigamos a enumeracao com ESTE rate limiting por IP
 * (~5 cadastros/15min), que inviabiliza a varredura em massa.
 *
 * Limpeza: entradas expiradas sao removidas ao consultar (sem thread extra).
 * Estado em memoria: some no restart, o que e aceitavel para o porte do
 * projeto (instancia unica) e ate desejavel na feira.
 */
@Service
public class RateLimitService {

    public static final String MENSAGEM_429 =
            "Muitas tentativas. Tente novamente em alguns minutos.";

    // Limites configuraveis: os testes de integracao compartilham o mesmo IP
    // (127.0.0.1 do MockMvc), entao a suite sobe os limites no properties de
    // teste e o RateLimitTest restaura os valores reais via @TestPropertySource.
    @Value("${app.ratelimit.max-falhas:5}")
    private int maxFalhas;

    @Value("${app.ratelimit.max-solicitacoes:5}")
    private int maxSolicitacoes;

    @Value("${app.ratelimit.janela-minutos:15}")
    private long janelaMinutos;

    private static class Falhas {
        int consecutivas;
        Instant expiraEm;
    }

    private static class Janela {
        int contagem;
        Instant expiraEm;
    }

    private final Map<String, Falhas> falhas = new ConcurrentHashMap<>();
    private final Map<String, Janela> janelas = new ConcurrentHashMap<>();

    // =========================
    // MODO 1 — falhas consecutivas
    // =========================

    /** true = chave atingiu o limite de falhas e ainda esta dentro do bloqueio. */
    public boolean bloqueadoPorFalhas(String escopo, String identificador) {
        limparExpirados();
        Falhas f = falhas.get(chave(escopo, identificador));
        return f != null
                && f.consecutivas >= maxFalhas
                && Instant.now().isBefore(f.expiraEm);
    }

    /** Registra uma falha; na N-esima, a chave fica bloqueada pela janela inteira. */
    public void registrarFalha(String escopo, String identificador) {
        falhas.compute(chave(escopo, identificador), (k, f) -> {
            Instant agora = Instant.now();
            if (f == null || agora.isAfter(f.expiraEm)) {
                f = new Falhas();
            }
            f.consecutivas++;
            // cada falha renova a janela: o bloqueio vale 15min apos a ultima falha
            f.expiraEm = agora.plus(janelaMinutos, ChronoUnit.MINUTES);
            return f;
        });
    }

    /** Sucesso zera o contador de falhas da chave. */
    public void limparFalhas(String escopo, String identificador) {
        falhas.remove(chave(escopo, identificador));
    }

    // =========================
    // MODO 2 — contagem de solicitacoes por IP
    // =========================

    /**
     * Conta ESTA solicitacao para o IP atual e retorna true quando o limite da
     * janela foi excedido (o chamador deve responder 429).
     */
    public boolean excedeuSolicitacoes(String escopo) {
        return excedeuSolicitacoes(escopo, maxSolicitacoes);
    }

    /**
     * Variante com limite CUSTOMIZADO por escopo (mesma janela). Usada pelo
     * assistente de IA, que precisa de um teto proprio (mais alto que o de
     * cadastro) para nao travar uma conversa, mas ainda proteger a cota da IA.
     */
    public boolean excedeuSolicitacoes(String escopo, int maxPersonalizado) {
        limparExpirados();
        Janela j = janelas.compute(chave(escopo, ipDaRequisicao()), (k, v) -> {
            Instant agora = Instant.now();
            if (v == null || agora.isAfter(v.expiraEm)) {
                v = new Janela();
                v.expiraEm = agora.plus(janelaMinutos, ChronoUnit.MINUTES);
            }
            v.contagem++;
            return v;
        });
        return j.contagem > maxPersonalizado;
    }

    // =========================
    // Apoio
    // =========================

    /** Resposta padrao 429 (mesma mensagem em todos os pontos limitados). */
    public static ResponseEntity<Map<String, String>> resposta429() {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", MENSAGEM_429);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(erro);
    }

    /** IP do cliente (mesmo criterio do AuditService: X-Forwarded-For > remoteAddr). */
    public String ipDaRequisicao() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "desconhecido";
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return "desconhecido";
        }
    }

    private String chave(String escopo, String identificador) {
        String id = identificador == null ? "?" : identificador.trim().toLowerCase();
        return escopo + ":" + id;
    }

    // Remove entradas cuja janela ja venceu. Chamado nas consultas — mantem os
    // mapas enxutos sem precisar de thread/agendador dedicado.
    private void limparExpirados() {
        Instant agora = Instant.now();
        falhas.entrySet().removeIf(e -> agora.isAfter(e.getValue().expiraEm));
        janelas.entrySet().removeIf(e -> agora.isAfter(e.getValue().expiraEm));
    }
}
