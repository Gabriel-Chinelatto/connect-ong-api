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

    /**
     * Relevancia minima para o fallback por regras devolver uma secao do
     * documento. Abaixo disso tratamos como pergunta fora de escopo.
     * (Titulo bate = +2, corpo bate = +1, por termo.)
     */
    private static final int ESCORE_MINIMO = 3;

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
                + "Seu UNICO assunto e como este projeto foi feito: arquitetura, "
                + "tecnologias, implementacao de cada funcionalidade, como a API funciona "
                + "por dentro, banco de dados, seguranca, IA, testes, hospedagem, decisoes "
                + "de projeto, equipe e historico de versoes.\n\n"
                + "REGRA 1 — FORA DE ESCOPO: se a pergunta nao tiver relacao com o "
                + "desenvolvimento do Connect ONG (ex.: futebol, receitas, politica, "
                + "celebridades, conselhos pessoais, duvidas gerais de programacao sem "
                + "ligacao com o projeto), NAO responda o assunto. Diga, de forma simpatica e "
                + "em UMA frase, que voce so fala sobre como o Connect ONG foi desenvolvido, "
                + "e ofereca dois exemplos de pergunta. Nao invente uma ponte artificial com "
                + "o projeto.\n"
                + "REGRA 2 — FIDELIDADE: use EXCLUSIVAMENTE o documento abaixo. Se o "
                + "documento nao cobrir o detalhe pedido, diga o que voce sabe sobre o tema e "
                + "admita com honestidade a parte que nao esta documentada. NAO invente "
                + "tecnologias, numeros, nomes, datas nem nomes de arquivos.\n"
                + "REGRA 3 — FORMATO: portugues do Brasil, claro e direto, no maximo uns 4 "
                + "paragrafos curtos ou uma lista. TEXTO PURO (nada de JSON ou markdown de "
                + "codigo, a menos que a pergunta peca um exemplo de codigo).\n\n"
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
        // Exige uma relacao MINIMA com o documento. Com o limiar antigo (score
        // > 0) bastava uma palavra qualquer coincidir para o assistente
        // despejar uma secao inteira: "qual seu time de futebol favorito?"
        // casava com "favoritos" e recebia o historico de versoes como
        // resposta. Fora de escopo, o certo e dizer que nao e o assunto.
        if (melhor == null || melhorScore < ESCORE_MINIMO) {
            return foraDeEscopo();
        }
        return "Sobre isso, o que está documentado no projeto:\n\n" + melhor.corpo.trim();
    }

    /** Recusa educada para perguntas que nao sao sobre o projeto. */
    private String foraDeEscopo() {
        return "Eu só falo sobre como o Connect ONG foi desenvolvido — arquitetura, "
                + "tecnologias, implementação das funcionalidades, banco de dados, "
                + "segurança, IA, testes e histórico de versões.\n\n"
                + "Pergunte algo como: \"Como funciona o match entre doador e ONG?\" ou "
                + "\"Como a API protege os dados de cada usuário?\".";
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
