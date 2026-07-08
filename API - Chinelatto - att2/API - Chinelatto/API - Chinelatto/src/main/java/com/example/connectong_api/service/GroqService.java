package com.example.connectong_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Provedor de IA usando a API GRATUITA da Groq (compativel com OpenAI):
 * {@code POST https://api.groq.com/openai/v1/chat/completions}.
 *
 * ============================================================================
 * COMO CONFIGURAR A CHAVE (o tier da Groq e GRATUITO — crie a chave em
 * https://console.groq.com/keys):
 *
 *   Opcao A (recomendada) — variavel de ambiente:
 *       APP_IA_GROQ_KEY=gsk_xxxxxxxxxxxxxxxxxxxx
 *
 *   Opcao B — application-local.properties (NAO versionado; ja no .gitignore):
 *       app.ia.groq.key=gsk_xxxxxxxxxxxxxxxxxxxx
 *
 * Sem chave (o padrao) o assistente funciona 100% no modo REGRAS (fallback
 * local). NUNCA comite a chave. A chave nunca e logada.
 *
 * Modelo padrao: llama-3.1-8b-instant (rapido e gratuito). Trocavel por
 * app.ia.groq.modelo. URL trocavel por app.ia.groq.url (permite apontar para
 * outro endpoint compativel com OpenAI).
 * ============================================================================
 */
@Service
public class GroqService implements ProvedorIA {

    @Value("${app.ia.groq.key:}")
    private String chave;

    @Value("${app.ia.groq.modelo:llama-3.1-8b-instant}")
    private String modelo;

    @Value("${app.ia.groq.url:https://api.groq.com/openai/v1/chat/completions}")
    private String url;

    // Temperatura baixa => respostas mais objetivas/consistentes (o assistente
    // precisa recomendar dados REAIS, nao inventar).
    @Value("${app.ia.groq.temperatura:0.4}")
    private double temperatura;

    // Timeout total da chamada (a IA nao pode travar a resposta ao doador).
    @Value("${app.ia.groq.timeout-segundos:15}")
    private long timeoutSegundos;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // HttpClient do JDK (java.net.http) — SEM dependencia nova.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public boolean disponivel() {
        return chave != null && !chave.isBlank();
    }

    @Override
    public Optional<String> completar(List<MensagemIA> mensagens) {
        if (!disponivel() || mensagens == null || mensagens.isEmpty()) {
            return Optional.empty();
        }

        try {
            String corpo = montarCorpo(mensagens);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSegundos))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + chave)
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build();

            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            // 429 (rate limit da cota), 5xx, etc. -> cai no fallback por regras.
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonNode raiz = objectMapper.readTree(resp.body());
            JsonNode conteudo = raiz.path("choices").path(0).path("message").path("content");
            if (conteudo.isMissingNode() || conteudo.isNull()) {
                return Optional.empty();
            }
            String texto = conteudo.asText("").trim();
            return texto.isBlank() ? Optional.empty() : Optional.of(texto);

        } catch (Exception e) {
            // Timeout, rede, JSON, qualquer coisa: silencioso -> fallback.
            // (NUNCA logar a chave nem o corpo, que carrega o header Authorization.)
            return Optional.empty();
        }
    }

    // Monta o JSON do chat/completions no formato OpenAI.
    private String montarCorpo(List<MensagemIA> mensagens) throws Exception {
        ObjectNode raiz = objectMapper.createObjectNode();
        raiz.put("model", modelo);
        raiz.put("temperature", temperatura);

        ArrayNode arr = raiz.putArray("messages");
        for (MensagemIA m : mensagens) {
            ObjectNode msg = arr.addObject();
            msg.put("role", m.papel());
            msg.put("content", m.conteudo());
        }
        return objectMapper.writeValueAsString(raiz);
    }
}
