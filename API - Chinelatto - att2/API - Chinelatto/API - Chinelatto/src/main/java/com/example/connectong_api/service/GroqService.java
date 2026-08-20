package com.example.connectong_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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
 * Modelo padrao (texto): ver app.ia.groq.modelo. URL trocavel por
 * app.ia.groq.url (permite apontar para outro endpoint compativel com OpenAI).
 *
 * ---------------------------------------------------------------------------
 * CADEIA DE MODELOS (app.ia.groq.modelos-reserva) — por que existe:
 *
 * Em 2026-08 a IA do projeto ficou SILENCIOSAMENTE morta: a Groq aposentou o
 * "llama-3.1-8b-instant" e passou a devolver 404 model_not_found em TODA
 * chamada. Como este service engole o erro e devolve Optional.empty(), os seis
 * recursos de IA cairam no fallback por regras ("Modo basico") sem que nada
 * aparecesse em lugar nenhum.
 *
 * Agora, quando o modelo principal responde um ERRO HTTP (404 = aposentado,
 * 429 = cota do minuto, 503 = sobrecarga), tentamos os modelos de reserva na
 * ordem. Isso resolve dois problemas de uma vez:
 *   - modelo aposentado nao derruba mais a IA (o reserva atende);
 *   - o free tier limita 8.000 tokens/MINUTO POR MODELO, e cada modelo tem o
 *     seu proprio balde: com 3 modelos na cadeia a fila da feira aguenta ~3x
 *     mais perguntas antes de cair no "Modo basico".
 * Falha de REDE/timeout nao tenta o proximo (seria somar 15s a cada tentativa
 * e deixar o usuario esperando); so erro HTTP, que volta em milissegundos.
 * ---------------------------------------------------------------------------
 *
 * Modelo de VISAO (multimodal, tambem gratuito): qwen/qwen3.6-27b — usado
 * quando o doador envia uma FOTO do que quer doar. E o unico do free tier que
 * aceita imagem (os gpt-oss respondem 400 "content must be a string"), por isso
 * a foto NAO usa a cadeia de reserva: se o modelo de visao falhar, repetimos
 * algumas vezes (ele devolve 503 "over capacity" com frequencia) e, por fim,
 * respondemos SEM a foto pelo caminho de texto — melhor uma resposta util sem
 * a imagem do que nenhuma resposta.
 * ============================================================================
 */
@Service
public class GroqService implements ProvedorIA {

    @Value("${app.ia.groq.key:}")
    private String chave;

    @Value("${app.ia.groq.modelo:openai/gpt-oss-120b}")
    private String modelo;

    /** Modelos tentados, na ordem, quando o principal devolve erro HTTP. */
    @Value("${app.ia.groq.modelos-reserva:openai/gpt-oss-20b,qwen/qwen3.6-27b}")
    private String modelosReserva;

    @Value("${app.ia.groq.modelo-visao:qwen/qwen3.6-27b}")
    private String modeloVisao;

    /** Tentativas no modelo de visao (ele devolve 503 "over capacity" com frequencia). */
    @Value("${app.ia.groq.tentativas-visao:3}")
    private int tentativasVisao;

    @Value("${app.ia.groq.url:https://api.groq.com/openai/v1/chat/completions}")
    private String url;

    // Temperatura baixa => respostas mais objetivas/consistentes (o assistente
    // precisa recomendar dados REAIS, nao inventar).
    @Value("${app.ia.groq.temperatura:0.4}")
    private double temperatura;

    // Timeout total da chamada (a IA nao pode travar a resposta ao doador).
    @Value("${app.ia.groq.timeout-segundos:15}")
    private long timeoutSegundos;

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- Diagnostico (lido por GET /ia/status; nunca expoe a chave) ----------
    // Guardamos so o ULTIMO resultado observado, para conferir na feira se a IA
    // esta mesmo respondendo sem precisar abrir os logs do Render.
    private volatile String ultimoModeloOk;   // modelo que respondeu por ultimo
    private volatile String ultimoErro;       // ex.: "404 openai/... model_not_found"

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
        return chamar(mensagens, null, null);
    }

    @Override
    public Optional<String> completar(List<MensagemIA> mensagens, OpcoesIA opcoes) {
        return chamar(mensagens, null, opcoes);
    }

    @Override
    public Optional<String> completarComImagem(List<MensagemIA> mensagens, String imagemBase64) {
        if (imagemBase64 == null || imagemBase64.isBlank()) {
            // Sem imagem: cai no fluxo de texto normal.
            return completar(mensagens);
        }
        return chamar(mensagens, imagemBase64, null);
    }

    // Ponto de entrada das duas modalidades. imagemBase64 == null => TEXTO (cadeia
    // de modelos); != null => VISAO (modelo multimodal, imagem anexada a ultima
    // msg do usuario, com queda para texto se a visao nao atender).
    // opcoes == null => usa os defaults (temperatura configurada, sem teto de tokens).
    private Optional<String> chamar(List<MensagemIA> mensagens, String imagemBase64, OpcoesIA opcoes) {
        if (!disponivel() || mensagens == null || mensagens.isEmpty()) {
            return Optional.empty();
        }
        boolean comImagem = imagemBase64 != null && !imagemBase64.isBlank();
        if (!comImagem) {
            return chamarTexto(mensagens, opcoes);
        }

        // VISAO: so o modelo multimodal aceita imagem. Ele vive dando 503 "over
        // capacity" no free tier, entao insistimos algumas vezes antes de
        // desistir da FOTO — e mesmo assim respondemos, so que sem ela.
        for (int i = 0; i < Math.max(1, tentativasVisao); i++) {
            Tentativa t = uma(modeloVisao, mensagens, imagemBase64, opcoes);
            if (t.texto() != null) return Optional.of(t.texto());
            if (!t.erroHttp()) break; // rede/timeout: insistir so faz o usuario esperar
        }
        log.warn("IA: visao indisponivel ({}), respondendo sem a foto", modeloVisao);
        return chamarTexto(mensagens, opcoes);
    }

    // TEXTO: tenta o modelo principal e, se ele devolver ERRO HTTP, os reservas.
    private Optional<String> chamarTexto(List<MensagemIA> mensagens, OpcoesIA opcoes) {
        for (String m : cadeiaDeModelos()) {
            Tentativa t = uma(m, mensagens, null, opcoes);
            if (t.texto() != null) {
                ultimoModeloOk = m;
                return Optional.of(t.texto());
            }
            // Rede/timeout: nao adianta trocar de modelo (o problema nao e o
            // modelo) e cada tentativa custaria mais 15s de espera.
            if (!t.erroHttp()) break;
        }
        return Optional.empty();
    }

    /** Modelo principal + reservas (sem repetidos, sem vazios), na ordem. */
    private List<String> cadeiaDeModelos() {
        List<String> lista = new ArrayList<>();
        if (modelo != null && !modelo.isBlank()) lista.add(modelo.trim());
        if (modelosReserva != null) {
            for (String m : modelosReserva.split(",")) {
                String limpo = m.trim();
                if (!limpo.isEmpty() && !lista.contains(limpo)) lista.add(limpo);
            }
        }
        return lista;
    }

    /**
     * UMA chamada HTTP a um modelo. Nunca lanca.
     * texto != null => sucesso. texto == null + erroHttp == true => o modelo
     * respondeu um erro (vale tentar o proximo da cadeia); erroHttp == false =>
     * timeout/rede/JSON (nao vale insistir).
     */
    private Tentativa uma(String modeloAlvo, List<MensagemIA> mensagens, String imagemBase64, OpcoesIA opcoes) {
        try {
            String corpo = montarCorpo(modeloAlvo, mensagens, imagemBase64, opcoes);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSegundos))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + chave)
                    .POST(HttpRequest.BodyPublishers.ofString(corpo))
                    .build();

            HttpResponse<String> resp =
                    httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            // 404 (modelo aposentado), 429 (cota do minuto), 503 (sobrecarga)...
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                registrarFalha(modeloAlvo, resp.statusCode(), resp.body());
                return Tentativa.erroDoModelo();
            }

            JsonNode raiz = objectMapper.readTree(resp.body());
            JsonNode conteudo = raiz.path("choices").path(0).path("message").path("content");
            String texto = conteudo.isMissingNode() || conteudo.isNull()
                    ? "" : limpar(conteudo.asText(""));
            if (texto.isBlank()) {
                // Resposta vazia costuma ser modelo "pensante" gastando o teto de
                // tokens no raciocinio: tratamos como erro do modelo (tenta o proximo).
                registrarFalha(modeloAlvo, resp.statusCode(), "resposta vazia");
                return Tentativa.erroDoModelo();
            }
            return Tentativa.ok(texto);

        } catch (Exception e) {
            // Timeout, rede, JSON. (NUNCA logar a chave nem o corpo da requisicao,
            // que carrega o header Authorization e pode conter a foto do doador.)
            ultimoErro = modeloAlvo + ": " + e.getClass().getSimpleName();
            log.warn("IA: falha de rede/timeout em {} ({})", modeloAlvo, e.getClass().getSimpleName());
            return Tentativa.falhaLocal();
        }
    }

    // Anota e LOGA a falha. Antes isto era 100% silencioso — foi por isso que a
    // IA passou dias inteira no "Modo basico" sem ninguem perceber. Logamos o
    // status, o modelo e a mensagem de erro da Groq (que nao tem dado nosso);
    // nunca a chave nem o corpo enviado.
    private void registrarFalha(String modeloAlvo, int status, String corpoResposta) {
        String motivo = "";
        try {
            JsonNode erro = objectMapper.readTree(corpoResposta).path("error").path("message");
            if (!erro.isMissingNode()) motivo = erro.asText("");
        } catch (Exception ignore) {
            // corpo nao-JSON (ex.: "resposta vazia"): usa como esta
            motivo = corpoResposta == null ? "" : corpoResposta;
        }
        if (motivo.length() > 200) motivo = motivo.substring(0, 200) + "...";
        ultimoErro = status + " " + modeloAlvo + (motivo.isBlank() ? "" : " — " + motivo);
        log.warn("IA: modelo {} respondeu HTTP {} — {}", modeloAlvo, status, motivo);
    }

    /**
     * Tira o bloco de raciocinio que alguns modelos "pensantes" (Qwen) deixam
     * escapar dentro do texto. Sem isto o doador leria "&lt;think&gt; the user
     * wants..." no meio da resposta.
     */
    private String limpar(String texto) {
        if (texto == null) return "";
        String t = texto;
        int ini = t.indexOf("<think>");
        if (ini >= 0) {
            int fim = t.indexOf("</think>", ini);
            t = fim >= 0 ? t.substring(0, ini) + t.substring(fim + 8) : t.substring(0, ini);
        }
        return t.trim();
    }

    /** Resultado de UMA chamada (ver {@link #uma}). */
    private record Tentativa(String texto, boolean erroHttp) {
        static Tentativa ok(String texto) { return new Tentativa(texto, false); }
        static Tentativa erroDoModelo() { return new Tentativa(null, true); }
        static Tentativa falhaLocal() { return new Tentativa(null, false); }
    }

    // Monta o JSON do chat/completions no formato OpenAI. Quando imagemBase64 != null,
    // usa o modelo de VISAO e anexa a imagem a ULTIMA mensagem "user" (content array
    // com {type:image_url}). As demais mensagens seguem como content string simples.
    private String montarCorpo(String modeloUsado, List<MensagemIA> mensagens, String imagemBase64, OpcoesIA opcoes) throws Exception {
        boolean comImagem = imagemBase64 != null && !imagemBase64.isBlank();

        ObjectNode raiz = objectMapper.createObjectNode();
        raiz.put("model", modeloUsado);

        // Modelos "pensantes" devolvem o raciocinio junto do texto e gastam o teto
        // de tokens pensando (resposta chega vazia). O parametro que desliga isso
        // tem valores DIFERENTES por familia, e quem nao o reconhece devolve 400 —
        // por isso so mandamos para quem sabemos que aceita:
        //   - Qwen3        -> "none" (desliga de vez)
        //   - OpenAI gpt-oss -> "low" (o minimo aceito; "none" da 400)
        String ml = modeloUsado == null ? "" : modeloUsado.toLowerCase();
        if (ml.contains("qwen3")) {
            raiz.put("reasoning_effort", "none");
        } else if (ml.contains("gpt-oss")) {
            raiz.put("reasoning_effort", "low");
        }

        // Temperatura: a da chamada (por tarefa) quando informada; senao o default.
        double temp = (opcoes != null && opcoes.temperatura() != null)
                ? opcoes.temperatura() : temperatura;
        raiz.put("temperature", temp);

        // Teto de tokens da resposta: so quando o chamador pede (>0). Limita
        // latencia/custo e evita respostas longas demais para a UI.
        if (opcoes != null && opcoes.maxTokens() != null && opcoes.maxTokens() > 0) {
            raiz.put("max_tokens", opcoes.maxTokens());
        }

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

    // ---- Diagnostico (ver ProvedorIA; nunca expoe a chave) ------------------

    /** Nome do modelo de visao em uso. */
    @Override
    public String modeloVisao() { return modeloVisao; }

    /** Cadeia de modelos de texto em uso, na ordem. */
    @Override
    public List<String> modelos() { return cadeiaDeModelos(); }

    @Override
    public String ultimoModeloOk() { return ultimoModeloOk; }

    @Override
    public String ultimoErro() { return ultimoErro; }

    /**
     * Chamada minima ao modelo principal (com os reservas) so para saber se a IA
     * responde de verdade. Usado pelo GET /ia/status?ping=true antes de apresentar.
     */
    @Override
    public boolean ping() {
        if (!disponivel()) return false;
        // 64 tokens, e nao 5: os modelos atuais "pensam" antes de responder e um
        // teto apertado demais devolve texto VAZIO — o ping acusaria falha com a
        // IA funcionando perfeitamente.
        return chamarTexto(List.of(new MensagemIA("user", "responda apenas: ok")),
                OpcoesIA.de(0.0, 64)).isPresent();
    }
}
