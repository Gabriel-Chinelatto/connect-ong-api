package com.example.connectong_api.service;

import com.example.connectong_api.dto.AssistenteRequestDTO;
import com.example.connectong_api.dto.AssistenteResponseDTO;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Assistente "Sobre o Desenvolvimento": responde perguntas sobre COMO o Connect
 * ONG foi construido (tecnologias, metodos, arquitetura, decisoes, seguranca,
 * uso de IA, equipe e historico de versoes).
 *
 * O "grounding" e um documento curado ({@code resources/dev/conhecimento_dev.md})
 * carregado no start. Reusa o MESMO provedor de IA (Groq) do
 * {@link AssistenteService} — nao precisa de nova chave. Se a IA estiver
 * indisponivel (sem chave, timeout, 429), cai num FALLBACK que devolve a secao
 * mais relevante do documento por palavras-chave: o assistente nunca deixa o
 * usuario sem resposta. Endpoint PUBLICO com rate limiting proprio ("assistente-dev").
 */
@Service
public class AssistenteDevService {

    /** So as ultimas trocas do historico dao contexto (limita tokens/latencia). */
    private static final int MAX_HISTORICO = 6;

    // Palavras muito comuns em PT-BR que nao ajudam a rankear secoes no fallback.
    private static final Set<String> STOP = Set.of(
            "como", "qual", "quais", "quando", "onde", "porque", "porquê", "por que", "para",
            "pra", "sobre", "isso", "esse", "essa", "este", "esta", "foi", "sao", "são",
            "que", "com", "sem", "dos", "das", "uma", "uns", "umas", "voce", "você", "projeto",
            "connect", "aplicativo", "app", "vocês", "voces", "fazer", "feito", "feita", "tem");

    @Autowired
    private ProvedorIA provedorIA;

    @Autowired
    private RateLimitService rateLimitService;

    @Value("${app.ia.dev.ratelimit.max:40}")
    private int maxDev;

    private String conhecimento = "";
    private List<Secao> secoes = new ArrayList<>();

    @PostConstruct
    void carregar() {
        try (InputStream in = new ClassPathResource("dev/conhecimento_dev.md").getInputStream()) {
            conhecimento = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            conhecimento = "";
        }
        secoes = dividirSecoes(conhecimento);
    }

    public ResponseEntity<?> responder(AssistenteRequestDTO dto) {
        if (rateLimitService.excedeuSolicitacoes("assistente-dev", maxDev)) {
            return RateLimitService.resposta429();
        }
        String pergunta = dto.getMensagem() == null ? "" : dto.getMensagem().trim();

        // 1) IA com grounding no documento (resposta natural).
        if (provedorIA.disponivel() && !conhecimento.isBlank()) {
            List<ProvedorIA.MensagemIA> mensagens = montarMensagens(dto, pergunta);
            Optional<String> saida = provedorIA.completar(mensagens, ProvedorIA.OpcoesIA.de(0.3, 700));
            if (saida.isPresent() && !saida.get().isBlank()) {
                return ResponseEntity.ok(new AssistenteResponseDTO(
                        saida.get().trim(), new ArrayList<>(), "ia"));
            }
        }
        // 2) Fallback por regras: melhor secao do documento.
        return ResponseEntity.ok(new AssistenteResponseDTO(
                responderPorRegras(pergunta), new ArrayList<>(), "regras"));
    }

    // ---- Montagem da conversa para a IA -------------------------------------

    private List<ProvedorIA.MensagemIA> montarMensagens(AssistenteRequestDTO dto, String pergunta) {
        List<ProvedorIA.MensagemIA> msgs = new ArrayList<>();
        msgs.add(new ProvedorIA.MensagemIA("system", systemPrompt()));
        List<AssistenteRequestDTO.MensagemHistorico> hist = dto.getHistorico();
        if (hist != null && !hist.isEmpty()) {
            int ini = Math.max(0, hist.size() - MAX_HISTORICO);
            for (int i = ini; i < hist.size(); i++) {
                AssistenteRequestDTO.MensagemHistorico h = hist.get(i);
                if (h == null || h.getTexto() == null || h.getTexto().isBlank()) continue;
                String papel = "assistente".equalsIgnoreCase(h.getPapel()) ? "assistant" : "user";
                msgs.add(new ProvedorIA.MensagemIA(papel, h.getTexto()));
            }
        }
        msgs.add(new ProvedorIA.MensagemIA("user", pergunta.isBlank() ? "Fale sobre o desenvolvimento do projeto." : pergunta));
        return msgs;
    }

    private String systemPrompt() {
        return "Voce e o assistente \"Sobre o Desenvolvimento\" do Connect ONG. "
                + "Responda perguntas sobre COMO o projeto foi feito: tecnologias, metodos, "
                + "arquitetura, decisoes de projeto, seguranca, uso de IA, equipe e historico "
                + "de versoes. Use EXCLUSIVAMENTE as informacoes do documento abaixo. Se a "
                + "resposta nao estiver no documento, diga com honestidade que nao tem essa "
                + "informacao e convide a pessoa a perguntar de outro jeito. Responda em "
                + "portugues do Brasil, de forma clara, amigavel e concisa (no maximo uns 4 "
                + "paragrafos curtos ou uma lista). NAO invente tecnologias, numeros, nomes ou "
                + "datas. Responda em TEXTO PURO (nada de JSON).\n\n"
                + "=== DOCUMENTO: COMO O CONNECT ONG FOI DESENVOLVIDO ===\n"
                + conhecimento;
    }

    // ---- Fallback por regras: escolhe a secao mais relevante ----------------

    private String responderPorRegras(String pergunta) {
        if (conhecimento.isBlank()) {
            return "No momento nao consigo acessar as informacoes de desenvolvimento do "
                    + "projeto. Tente novamente daqui a pouco.";
        }
        List<String> termos = termos(pergunta);
        Secao melhor = null;
        int melhorScore = 0;
        for (Secao s : secoes) {
            String alvo = (s.titulo + " " + s.corpo).toLowerCase();
            int score = 0;
            for (String t : termos) if (alvo.contains(t)) score++;
            // Peso extra quando o termo aparece no titulo da secao.
            String tituloLower = s.titulo.toLowerCase();
            for (String t : termos) if (tituloLower.contains(t)) score += 2;
            if (score > melhorScore) { melhorScore = score; melhor = s; }
        }
        if (melhor == null || melhorScore == 0) {
            return "Posso te contar como o Connect ONG foi desenvolvido: as tecnologias "
                    + "(Java/Spring Boot no backend, Flutter no mobile e no desktop, HTML/CSS/"
                    + "JavaScript na web, MySQL no banco), os metodos, as decisoes de projeto e "
                    + "o historico de versoes (da v1.0 a v2.0). Pergunte algo como: \"Qual e a "
                    + "stack?\", \"Quando a IA foi adicionada?\" ou \"Por que a web foi feita em "
                    + "HTML puro?\".";
        }
        return melhor.corpo.trim();
    }

    private List<String> termos(String pergunta) {
        List<String> out = new ArrayList<>();
        for (String w : pergunta.toLowerCase().split("[^a-z0-9\\u00e0-\\u00ff]+")) {
            if (w.length() >= 4 && !STOP.contains(w)) out.add(w);
        }
        return out;
    }

    // ---- Divisao do documento em secoes (por cabecalhos "## ") --------------

    private List<Secao> dividirSecoes(String doc) {
        List<Secao> lista = new ArrayList<>();
        if (doc == null || doc.isBlank()) return lista;
        String titulo = "Introducao";
        StringBuilder corpo = new StringBuilder();
        for (String linha : doc.split("\\r?\\n")) {
            if (linha.startsWith("## ")) {
                if (corpo.length() > 0) lista.add(new Secao(titulo, corpo.toString()));
                titulo = linha.substring(3).trim();
                corpo = new StringBuilder();
                corpo.append(titulo).append('\n');
            } else if (linha.startsWith("# ")) {
                // titulo do documento — ignora como secao
            } else {
                corpo.append(linha).append('\n');
            }
        }
        if (corpo.length() > 0) lista.add(new Secao(titulo, corpo.toString()));
        return lista;
    }

    /** Uma secao do documento de conhecimento (titulo do cabecalho + corpo). */
    private static class Secao {
        final String titulo;
        final String corpo;
        Secao(String titulo, String corpo) { this.titulo = titulo; this.corpo = corpo; }
    }
}
