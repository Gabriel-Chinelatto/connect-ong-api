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
 * Modelo padrao (texto): llama-3.1-8b-instant (rapido e gratuito). Trocavel por
 * app.ia.groq.modelo. URL trocavel por app.ia.groq.url (permite apontar para
 * outro endpoint compativel com OpenAI).
 *
 * Modelo de VISAO (multimodal, tambem gratuito): meta-llama/llama-4-maverick-
 * 17b-128e-instruct — usado quando o doador envia uma FOTO do que quer doar.
 * Trocavel por app.ia.groq.modelo-visao. (NAO usar o "scout", ja deprecado.)
 * ============================================================================
 */
@Service
public class GroqService implements ProvedorIA {

    @Value("${app.ia.groq.key:}")
    private String chave;

    @Value("${app.ia.groq.modelo:llama-3.1-8b-instant}")
    private String modelo;

    @Value("${app.ia.groq.modelo-visao:meta-llama/llama-4-maverick-17b-128e-instruct}")
    private String modeloVisao;

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
        return chamar(mensagens, null);
    }

    @Override
    public Optional<String> completarComImagem(List<MensagemIA> mensagens, String imagemBase64) {
        if (imagemBase64 == null || imagemBase64.isBlank()) {
            // Sem imagem: cai no fluxo de texto normal.
            return completar(mensagens);
        }
        return chamar(mensagens, imagemBase64);
    }

    // Faz a chamada HTTP. imagemBase64 == null => texto (modelo de texto);
    // != null => visao (modelo multimodal, imagem anexada a ultima msg do usuario).
    private Optional<String> chamar(List<MensagemIA> mensagens, String imagemBase64) {
        if (!disponivel() || mensagens == null || mensagens.isEmpty()) {
            return Optional.empty();
        }

        try {
            String corpo = montarCorpo(mensagens, imagemBase64);

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

    // Monta o JSON do chat/completions no formato OpenAI. Quando imagemBase64 != null,
    // usa o modelo de VISAO e anexa a imagem a ULTIMA mensagem "user" (content array
    // com {type:image_url}). As demais mensagens seguem como content string simples.
    private String montarCorpo(List<MensagemIA> mensagens, String imagemBase64) throws Exception {
        boolean comImagem = imagemBase64 != null && !imagemBase64.isBlank();

        ObjectNode raiz = objectMapper.createObjectNode();
        raiz.put("model", comImagem ? modeloVisao : modelo);
        raiz.put("temperature", temperatura);

        // Indice da ultima mensagem do usuario (onde a imagem sera anexada).
        int idxUltimoUser = -1;
        if (comImagem) {
            for (int i = mensagens.size() - 1; i >= 0; i--) {
                if ("user".equals(mensagens.get(i).papel())) { idxUltimoUser = i; break; }
            }
        }

        ArrayNode arr = raiz.putArray("messages");
        for (int i = 0; i < mensagens.size(); i++) {
            MensagemIA m = mensagens.get(i);
            ObjectNode msg = arr.addObject();
            msg.put("role", m.papel());
            if (comImagem && i == idxUltimoUser) {
                // content array: texto + imagem (formato multimodal OpenAI-compat).
                ArrayNode conteudo = msg.putArray("content");
                ObjectNode parteTexto = conteudo.addObject();
                parteTexto.put("type", "text");
                parteTexto.put("text", m.conteudo() == null || m.conteudo().isBlank()
                        ? "Descreva o que aparece nesta foto e para quem eu poderia doar."
                        : m.conteudo());
                ObjectNode parteImg = conteudo.addObject();
                parteImg.put("type", "image_url");
                ObjectNode urlNode = parteImg.putObject("image_url");
                urlNode.put("url", comoDataUrl(imagemBase64));
            } else {
                msg.put("content", m.conteudo());
            }
        }
        return objectMapper.writeValueAsString(raiz);
    }

    // Garante o prefixo data URL. Se ja vier "data:...;base64,..." usa como esta;
    // se vier base64 puro, assume image/jpeg. (Nao logamos o conteudo.)
    private String comoDataUrl(String imagem) {
        String s = imagem.trim();
        if (s.startsWith("data:")) return s;
        return "data:image/jpeg;base64," + s;
    }

    /** Nome do modelo de visao em uso (para diagnostico; nunca expoe a chave). */
    public String getModeloVisao() { return modeloVisao; }
}
