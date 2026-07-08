package com.example.connectong_api.service;

import com.example.connectong_api.dto.AssistenteRequestDTO;
import com.example.connectong_api.dto.AssistenteResponseDTO;
import com.example.connectong_api.dto.AssistenteResponseDTO.Sugestao;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.security.UsuarioAutenticado;
import com.example.connectong_api.util.Categorias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * "Assistente de doacao": chatbot que ajuda o DOADOR a decidir para quem doar.
 * Responde perguntas como "tenho tais coisas, para quem doo?", "moro em X, quero
 * ONGs perto" e "o que a ONG Y precisa?".
 *
 * Duas camadas:
 *  1) IA (Groq, gratuita) — grounded com DADOS REAIS do sistema (ONGs ativas e
 *     necessidades abertas). A IA recebe um system prompt descrevendo o Connect
 *     ONG + a lista real, e responde em JSON {resposta, sugestoes:[{tipo,id}]}.
 *  2) FALLBACK por REGRAS — quando NAO ha chave da IA, ou a IA falha/timeout/429.
 *     Interpreta a mensagem por palavras-chave (categorias, "perto", "o que
 *     precisa", nome de ONG) e busca nos MESMOS dados reais. Nunca lanca erro:
 *     sempre devolve algo util. Marca modo:"regras".
 *
 * As sugestoes SEMPRE apontam para ONGs/necessidades reais (ids validos), pois o
 * app as exibe como cards clicaveis. Mesmo quando a IA responde, os ids sao
 * revalidados contra os dados reais (a IA nao "inventa" cards).
 */
@Service
public class AssistenteService {

    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SecurityUtils security;
    @Autowired private RateLimitService rateLimitService;
    @Autowired private ProvedorIA provedorIA;

    // Teto de mensagens por IP/janela (protege a cota gratuita da Groq). Alto o
    // bastante para uma conversa normal; nos testes o properties o desliga.
    @Value("${app.ia.ratelimit.max:30}")
    private int maxAssistente;

    private static final int LIMITE_NECESSIDADES = 35;
    private static final int LIMITE_ONGS = 20;
    private static final int MAX_SUGESTOES = 6;
    private static final int MAX_HISTORICO = 6;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Palavras-chave -> categoria canonica (usadas no fallback por regras).
    private static final Map<String, List<String>> PALAVRAS_CATEGORIA = new LinkedHashMap<>();
    static {
        PALAVRAS_CATEGORIA.put("Alimentos", List.of(
                "aliment", "comida", "cesta", "cesta basica", "leite", "arroz",
                "feijao", "mantimento", "racao", "nao pereciv", "marmita"));
        PALAVRAS_CATEGORIA.put("Roupas", List.of(
                "roupa", "agasalho", "casaco", "cobertor", "blusa", "calca",
                "sapato", "calcado", "moletom", "manta"));
        PALAVRAS_CATEGORIA.put("Higiene", List.of(
                "higiene", "fralda", "sabonete", "shampoo", "absorvente",
                "papel higienico", "pasta de dente", "escova", "sabao"));
        PALAVRAS_CATEGORIA.put("Brinquedos", List.of(
                "brinquedo", "jogo", "boneca", "bola", "pelucia", "quebra-cabeca"));
        PALAVRAS_CATEGORIA.put("Educacao", List.of(
                "educ", "escolar", "caderno", "livro", "mochila", "lapis",
                "caneta", "material escolar", "apostila"));
        PALAVRAS_CATEGORIA.put("Saude", List.of(
                "saude", "remedio", "medicament", "veterinari", "mascara",
                "curativo", "fisioterap"));
    }

    // ================================================================
    // ENTRADA PRINCIPAL
    // ================================================================
    public ResponseEntity<?> responder(AssistenteRequestDTO dto) {
        // Rate limit por IP (protege a cota da IA e evita abuso). Teto proprio.
        if (rateLimitService.excedeuSolicitacoes("assistente", maxAssistente)) {
            return RateLimitService.resposta429();
        }

        String mensagem = dto.getMensagem() != null ? dto.getMensagem().trim() : "";
        String cidade = resolverCidade(dto);

        // Dados reais (ONGs ativas + necessidades abertas), priorizando a cidade.
        Contexto ctx = montarContexto(cidade);

        // 1) Tenta a IA (se ha chave e ela responde). Senao, cai no fallback.
        if (provedorIA.disponivel()) {
            AssistenteResponseDTO viaIa = tentarIa(dto, mensagem, cidade, ctx);
            if (viaIa != null) {
                return ResponseEntity.ok(viaIa);
            }
        }

        // 2) FALLBACK por regras (sempre funciona, sem chave).
        return ResponseEntity.ok(responderPorRegras(mensagem, cidade, ctx, "regras"));
    }

    // ================================================================
    // CAMINHO DA IA
    // ================================================================
    private AssistenteResponseDTO tentarIa(AssistenteRequestDTO dto, String mensagem,
                                           String cidade, Contexto ctx) {
        List<ProvedorIA.MensagemIA> mensagens = new ArrayList<>();
        mensagens.add(new ProvedorIA.MensagemIA("system", systemPrompt(cidade, ctx)));

        // Historico truncado (ultimas MAX_HISTORICO trocas).
        if (dto.getHistorico() != null) {
            List<AssistenteRequestDTO.MensagemHistorico> h = dto.getHistorico();
            int inicio = Math.max(0, h.size() - MAX_HISTORICO);
            for (int i = inicio; i < h.size(); i++) {
                AssistenteRequestDTO.MensagemHistorico m = h.get(i);
                if (m == null || m.getTexto() == null || m.getTexto().isBlank()) continue;
                String papel = "assistente".equalsIgnoreCase(m.getPapel())
                        ? "assistant" : "user";
                mensagens.add(new ProvedorIA.MensagemIA(papel, m.getTexto().trim()));
            }
        }
        mensagens.add(new ProvedorIA.MensagemIA("user", mensagem));

        Optional<String> saida = provedorIA.completar(mensagens);
        if (saida.isEmpty()) {
            return null; // IA indisponivel/falhou -> fallback
        }

        String texto = saida.get();

        // Tenta interpretar como JSON {resposta, sugestoes:[{tipo,id}]}.
        AssistenteResponseDTO estruturada = parsearJsonIa(texto, ctx);
        if (estruturada != null) {
            return estruturada;
        }

        // A IA respondeu texto puro (nao-JSON): usa o texto como resposta e
        // deriva as sugestoes por busca simples nos dados reais. Modo continua "ia".
        AssistenteResponseDTO porRegras = responderPorRegras(mensagem, cidade, ctx, "ia");
        return new AssistenteResponseDTO(texto.trim(), porRegras.getSugestoes(), "ia");
    }

    /**
     * Parse ROBUSTO do JSON da IA. Extrai o objeto entre a primeira '{' e a
     * ultima '}' (a IA as vezes envolve o JSON em texto). Devolve null se nao
     * conseguir montar uma resposta valida (o chamador degrada para texto puro).
     */
    private AssistenteResponseDTO parsearJsonIa(String texto, Contexto ctx) {
        try {
            int ini = texto.indexOf('{');
            int fim = texto.lastIndexOf('}');
            if (ini < 0 || fim <= ini) return null;

            JsonNode raiz = objectMapper.readTree(texto.substring(ini, fim + 1));
            String resposta = raiz.path("resposta").asText("").trim();
            if (resposta.isBlank()) return null;

            List<Sugestao> sugestoes = new ArrayList<>();
            Set<String> vistos = new LinkedHashSet<>();
            JsonNode arr = raiz.path("sugestoes");
            if (arr.isArray()) {
                for (JsonNode s : arr) {
                    String tipo = s.path("tipo").asText("").trim().toUpperCase();
                    long id = s.path("id").asLong(-1);
                    if (id < 0) continue;
                    Sugestao card = montarCardValidado(tipo, id, ctx);
                    if (card != null && vistos.add(card.getTipo() + ":" + card.getId())) {
                        sugestoes.add(card);
                        if (sugestoes.size() >= MAX_SUGESTOES) break;
                    }
                }
            }
            return new AssistenteResponseDTO(resposta, sugestoes, "ia");
        } catch (Exception e) {
            return null;
        }
    }

    // So aceita ids que existem de fato nos dados reais (a IA nao inventa cards).
    private Sugestao montarCardValidado(String tipo, long id, Contexto ctx) {
        if ("NECESSIDADE".equals(tipo)) {
            Necessidade n = ctx.necessidadePorId.get(id);
            if (n != null) return cardNecessidade(n);
        } else if ("ONG".equals(tipo)) {
            Ong o = ctx.ongPorId.get(id);
            if (o != null) return cardOng(o);
        }
        return null;
    }

    // System prompt: descreve o Connect ONG + injeta os dados reais.
    private String systemPrompt(String cidade, Contexto ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Voce e o assistente virtual do Connect ONG, uma plataforma que ")
          .append("conecta DOADORES a ONGs. As ONGs publicam NECESSIDADES (o que ")
          .append("precisam receber); o doador demonstra interesse, forma um match, ")
          .append("conversa no chat e combina a entrega, ou doa dinheiro via PIX. ")
          .append("Seu papel: ajudar o DOADOR a decidir para quem/o que doar.\n\n");

        if (cidade != null && !cidade.isBlank()) {
            sb.append("Cidade do doador: ").append(cidade).append(".\n\n");
        }

        sb.append("ONGs ativas (use SOMENTE estas ao recomendar):\n");
        for (Ong o : ctx.ongs) {
            sb.append("- [id=").append(o.getId()).append("] ").append(o.getNome());
            if (temTexto(o.getCidade())) sb.append(" (").append(o.getCidade()).append(")");
            if (o.getVerificada()) sb.append(" - verificada");
            if (o.getTotalAvaliacoes() != null && o.getTotalAvaliacoes() > 0) {
                sb.append(" - nota ").append(o.getNotaMedia());
            }
            List<String> cats = ctx.categoriasPorOng.get(o.getId());
            if (cats != null && !cats.isEmpty()) {
                sb.append(" - costuma pedir: ").append(String.join(", ", cats));
            }
            sb.append("\n");
        }

        sb.append("\nNecessidades abertas (use SOMENTE estas ao recomendar):\n");
        for (Necessidade n : ctx.necessidades) {
            sb.append("- [id=").append(n.getId()).append("] ").append(n.getTitulo());
            if (temTexto(n.getCategoria())) sb.append(" (").append(n.getCategoria()).append(")");
            if (n.getOng() != null) {
                sb.append(" - ONG ").append(n.getOng().getNome());
                if (temTexto(n.getOng().getCidade())) {
                    sb.append(", ").append(n.getOng().getCidade());
                }
            }
            if (Boolean.TRUE.equals(n.getUrgente())) sb.append(" - URGENTE");
            sb.append("\n");
        }

        sb.append("\nRegras: responda SEMPRE em portugues do Brasil, de forma breve, ")
          .append("acolhedora e objetiva. Recomende apenas ONGs/necessidades da lista ")
          .append("acima, citando o nome e a cidade. Se o doador informou a cidade, ")
          .append("priorize o que estiver perto. Se nada casar exatamente, sugira as ")
          .append("opcoes mais proximas e explique. Nao invente ONGs, dados de contato ")
          .append("nem PIX.\n")
          .append("Responda EXCLUSIVAMENTE com um JSON valido, sem texto fora dele, ")
          .append("no formato: {\"resposta\":\"seu texto\",\"sugestoes\":[{\"tipo\":")
          .append("\"NECESSIDADE\",\"id\":123},{\"tipo\":\"ONG\",\"id\":45}]}. ")
          .append("Inclua em sugestoes os ids reais das opcoes citadas (no maximo ")
          .append(MAX_SUGESTOES).append("). Se nao houver o que sugerir, use lista vazia.");
        return sb.toString();
    }

    // ================================================================
    // FALLBACK POR REGRAS
    // ================================================================
    private AssistenteResponseDTO responderPorRegras(String mensagem, String cidade,
                                                     Contexto ctx, String modo) {
        String norm = normalizar(mensagem);

        // (C) Pergunta sobre uma ONG especifica citada pelo nome.
        Ong ongCitada = ongCitadaNaMensagem(norm, ctx);
        if (ongCitada != null && (norm.contains("precis") || norm.contains("necessit")
                || norm.contains("o que") || norm.contains("pode doar")
                || norm.contains("posso doar") || norm.contains("ajud"))) {
            return respostaNecessidadesDaOng(ongCitada, ctx, modo);
        }

        // (A) Doacao de itens: uma ou mais categorias reconhecidas na mensagem.
        Set<String> categorias = categoriasDetectadas(norm);
        if (!categorias.isEmpty()) {
            return respostaPorCategoria(categorias, cidade, ctx, modo);
        }

        // (B) Intencao de proximidade/cidade (sem categoria).
        boolean querPerto = norm.contains("perto") || norm.contains("proxim")
                || norm.contains("por perto") || norm.contains("minha cidade")
                || norm.contains("aqui em") || norm.contains("na regiao")
                || (temTexto(cidade) && norm.contains(normalizar(cidade)));
        if (querPerto || ongCitada != null) {
            return respostaPorProximidade(cidade, ctx, modo);
        }

        // (D) Default / saudacao: mostra um panorama util.
        return respostaPadrao(cidade, ctx, modo);
    }

    private AssistenteResponseDTO respostaPorCategoria(Set<String> categorias, String cidade,
                                                       Contexto ctx, String modo) {
        List<Necessidade> casam = new ArrayList<>();
        for (Necessidade n : ctx.necessidades) {
            if (categorias.stream().anyMatch(c -> Categorias.iguais(n.getCategoria(), c))) {
                casam.add(n);
            }
        }
        ordenarNecessidades(casam, cidade);

        String catTexto = juntarCategorias(categorias);
        StringBuilder sb = new StringBuilder();
        List<Sugestao> sugestoes = new ArrayList<>();

        if (!casam.isEmpty()) {
            sb.append("Que generosidade! Encontrei ")
              .append(casam.size() == 1 ? "uma necessidade" : "algumas necessidades")
              .append(" de ").append(catTexto);
            if (temTexto(cidade)) sb.append(" perto de ").append(cidade);
            sb.append(". Veja se alguma combina com o que voce tem para doar:");
            for (Necessidade n : limitar(casam, MAX_SUGESTOES)) {
                sugestoes.add(cardNecessidade(n));
            }
        } else {
            // Sem match exato: oferece ONGs proximas / necessidades recentes.
            sb.append("No momento nao achei uma necessidade aberta de ").append(catTexto);
            if (temTexto(cidade)) sb.append(" em ").append(cidade);
            sb.append(", mas estas ONGs recebem doacoes e podem se interessar:");
            sugestoes = cardsOngsProximas(cidade, ctx);
            if (sugestoes.isEmpty()) {
                for (Necessidade n : limitar(ctx.necessidades, MAX_SUGESTOES)) {
                    sugestoes.add(cardNecessidade(n));
                }
            }
        }
        return new AssistenteResponseDTO(sb.toString(), sugestoes, modo);
    }

    private AssistenteResponseDTO respostaPorProximidade(String cidade, Contexto ctx, String modo) {
        StringBuilder sb = new StringBuilder();
        List<Sugestao> sugestoes = cardsOngsProximas(cidade, ctx);

        if (!sugestoes.isEmpty()) {
            sb.append(temTexto(cidade)
                    ? "Perto de " + cidade + ", estas ONGs estao recebendo doacoes:"
                    : "Estas ONGs estao ativas e recebendo doacoes:");
        } else {
            sb.append("Ainda nao encontrei ONGs cadastradas");
            if (temTexto(cidade)) sb.append(" em ").append(cidade);
            sb.append(". Enquanto isso, veja necessidades abertas em outras cidades:");
            for (Necessidade n : limitar(ctx.necessidades, MAX_SUGESTOES)) {
                sugestoes.add(cardNecessidade(n));
            }
        }
        return new AssistenteResponseDTO(sb.toString(), sugestoes, modo);
    }

    private AssistenteResponseDTO respostaNecessidadesDaOng(Ong ong, Contexto ctx, String modo) {
        List<Necessidade> daOng = new ArrayList<>();
        for (Necessidade n : ctx.necessidades) {
            if (n.getOng() != null && ong.getId().equals(n.getOng().getId())) {
                daOng.add(n);
            }
        }
        StringBuilder sb = new StringBuilder();
        List<Sugestao> sugestoes = new ArrayList<>();
        if (!daOng.isEmpty()) {
            sb.append(ong.getNome()).append(" precisa hoje de: ");
            List<String> titulos = new ArrayList<>();
            for (Necessidade n : limitar(daOng, MAX_SUGESTOES)) {
                titulos.add(n.getTitulo());
                sugestoes.add(cardNecessidade(n));
            }
            sb.append(String.join(", ", titulos)).append(". Toque para ver os detalhes.");
        } else {
            sb.append(ong.getNome())
              .append(" nao tem necessidades abertas no momento, mas voce pode abrir ")
              .append("o perfil dela para conhecer o trabalho e doar via PIX.");
            sugestoes.add(cardOng(ong));
        }
        return new AssistenteResponseDTO(sb.toString(), sugestoes, modo);
    }

    private AssistenteResponseDTO respostaPadrao(String cidade, Contexto ctx, String modo) {
        StringBuilder sb = new StringBuilder();
        sb.append("Oi! Sou o assistente do Connect ONG e posso te ajudar a doar. ")
          .append("Me conte o que voce tem para doar (roupas, alimentos, higiene, ")
          .append("brinquedos, material escolar...) ou de que cidade voce e, que eu ")
          .append("sugiro ONGs e necessidades reais. Enquanto isso, veja algumas que ")
          .append("estao precisando agora:");

        List<Sugestao> sugestoes = new ArrayList<>();
        // Prioriza urgentes/proximas.
        List<Necessidade> destaque = new ArrayList<>(ctx.necessidades);
        ordenarNecessidades(destaque, cidade);
        for (Necessidade n : limitar(destaque, MAX_SUGESTOES)) {
            sugestoes.add(cardNecessidade(n));
        }
        return new AssistenteResponseDTO(sb.toString(), sugestoes, modo);
    }

    // ================================================================
    // MONTAGEM DO CONTEXTO (dados reais)
    // ================================================================
    private Contexto montarContexto(String cidade) {
        Contexto ctx = new Contexto();

        // ONGs ativas.
        List<Ong> ongs = new ArrayList<>();
        for (Ong o : ongRepository.findAll()) {
            if (o.getDataExclusao() == null) ongs.add(o);
        }
        ordenarOngs(ongs, cidade);
        ctx.ongs = limitar(ongs, LIMITE_ONGS);
        for (Ong o : ctx.ongs) ctx.ongPorId.put(o.getId(), o);

        // Necessidades abertas (status ABERTA ou nulo, de ONG ativa).
        List<Necessidade> necessidades = new ArrayList<>();
        for (Necessidade n : necessidadeRepository.findAll()) {
            boolean aberta = n.getStatus() == null || n.getStatus().isBlank()
                    || "ABERTA".equalsIgnoreCase(n.getStatus());
            boolean ongAtiva = n.getOng() != null && n.getOng().getDataExclusao() == null;
            if (aberta && ongAtiva) necessidades.add(n);
        }
        ordenarNecessidades(necessidades, cidade);
        ctx.necessidades = limitar(necessidades, LIMITE_NECESSIDADES);
        for (Necessidade n : ctx.necessidades) ctx.necessidadePorId.put(n.getId(), n);

        // Categorias que cada ONG mais pede (a partir das necessidades abertas).
        for (Necessidade n : ctx.necessidades) {
            if (n.getOng() == null || !temTexto(n.getCategoria())) continue;
            ctx.categoriasPorOng
                    .computeIfAbsent(n.getOng().getId(), k -> new ArrayList<>());
            List<String> lista = ctx.categoriasPorOng.get(n.getOng().getId());
            String cat = Categorias.normalizar(n.getCategoria());
            if (!lista.contains(cat)) lista.add(cat);
        }
        return ctx;
    }

    // ================================================================
    // ORDENACAO / HELPERS
    // ================================================================
    // Necessidades: cidade do doador primeiro, depois urgentes, depois recentes.
    private void ordenarNecessidades(List<Necessidade> lista, String cidade) {
        String cid = normalizar(cidade);
        lista.sort(Comparator
                .comparing((Necessidade n) -> mesmaCidadeNec(n, cid) ? 0 : 1)
                .thenComparing(n -> Boolean.TRUE.equals(n.getUrgente()) ? 0 : 1)
                .thenComparing(n -> n.getDataCriacao() != null
                        ? n.getDataCriacao() : LocalDateTime.MIN, Comparator.reverseOrder()));
    }

    // ONGs: cidade do doador primeiro, depois verificadas, depois melhor nota.
    private void ordenarOngs(List<Ong> lista, String cidade) {
        String cid = normalizar(cidade);
        lista.sort(Comparator
                .comparing((Ong o) -> temTexto(cid) && cid.equals(normalizar(o.getCidade())) ? 0 : 1)
                .thenComparing(o -> o.getVerificada() ? 0 : 1)
                .thenComparing(Ong::getNotaMedia, Comparator.reverseOrder()));
    }

    private boolean mesmaCidadeNec(Necessidade n, String cidadeNorm) {
        if (!temTexto(cidadeNorm) || n.getOng() == null) return false;
        return cidadeNorm.equals(normalizar(n.getOng().getCidade()));
    }

    private List<Sugestao> cardsOngsProximas(String cidade, Contexto ctx) {
        String cid = normalizar(cidade);
        List<Sugestao> proximas = new ArrayList<>();
        // Primeiro as da cidade (quando informada).
        if (temTexto(cid)) {
            for (Ong o : ctx.ongs) {
                if (cid.equals(normalizar(o.getCidade()))) proximas.add(cardOng(o));
                if (proximas.size() >= MAX_SUGESTOES) return proximas;
            }
        }
        // Completa com ONGs verificadas / bem avaliadas.
        for (Ong o : ctx.ongs) {
            if (proximas.size() >= MAX_SUGESTOES) break;
            Sugestao card = cardOng(o);
            if (proximas.stream().noneMatch(s -> s.getId().equals(card.getId()))) {
                proximas.add(card);
            }
        }
        return limitar(proximas, MAX_SUGESTOES);
    }

    private Sugestao cardNecessidade(Necessidade n) {
        StringBuilder sub = new StringBuilder();
        if (n.getOng() != null) {
            sub.append(n.getOng().getNome());
            if (temTexto(n.getOng().getCidade())) {
                sub.append(" - ").append(n.getOng().getCidade());
            }
        }
        if (Boolean.TRUE.equals(n.getUrgente())) {
            if (sub.length() > 0) sub.append(" - ");
            sub.append("Urgente");
        }
        return new Sugestao("NECESSIDADE", n.getId(), n.getTitulo(), sub.toString());
    }

    private Sugestao cardOng(Ong o) {
        StringBuilder sub = new StringBuilder();
        if (temTexto(o.getCidade())) sub.append(o.getCidade());
        if (o.getVerificada()) {
            if (sub.length() > 0) sub.append(" - ");
            sub.append("Verificada");
        }
        return new Sugestao("ONG", o.getId(), o.getNome(), sub.toString());
    }

    // Cidade: a do body tem prioridade; senao a do perfil do usuario logado.
    private String resolverCidade(AssistenteRequestDTO dto) {
        if (temTexto(dto.getCidade())) return dto.getCidade().trim();
        UsuarioAutenticado atual = security.atual();
        if (atual != null && atual.getId() != null) {
            Usuario u = usuarioRepository.findById(atual.getId()).orElse(null);
            if (u != null && temTexto(u.getCidade())) return u.getCidade().trim();
        }
        return null;
    }

    private Set<String> categoriasDetectadas(String norm) {
        Set<String> achadas = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> e : PALAVRAS_CATEGORIA.entrySet()) {
            for (String chave : e.getValue()) {
                if (norm.contains(chave)) {
                    achadas.add(e.getKey());
                    break;
                }
            }
        }
        return achadas;
    }

    private Ong ongCitadaNaMensagem(String norm, Contexto ctx) {
        Ong melhor = null;
        int melhorTam = 0;
        for (Ong o : ctx.ongs) {
            String nomeNorm = normalizar(o.getNome());
            // Evita casar nomes muito curtos (ruido). Exige >= 4 chars.
            if (nomeNorm.length() >= 4 && norm.contains(nomeNorm) && nomeNorm.length() > melhorTam) {
                melhor = o;
                melhorTam = nomeNorm.length();
            }
        }
        return melhor;
    }

    private String juntarCategorias(Set<String> categorias) {
        List<String> l = new ArrayList<>(categorias);
        if (l.size() == 1) return l.get(0).toLowerCase();
        return String.join(", ", l).toLowerCase();
    }

    private <T> List<T> limitar(List<T> lista, int max) {
        return lista.size() <= max ? lista : new ArrayList<>(lista.subList(0, max));
    }

    private boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    // Minusculas + sem acento (para casar palavras-chave e cidades/nomes).
    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().trim();
    }

    // Recorte dos dados reais que alimentam a IA e o fallback.
    private static class Contexto {
        List<Ong> ongs = new ArrayList<>();
        List<Necessidade> necessidades = new ArrayList<>();
        Map<Long, Ong> ongPorId = new LinkedHashMap<>();
        Map<Long, Necessidade> necessidadePorId = new LinkedHashMap<>();
        Map<Long, List<String>> categoriasPorOng = new LinkedHashMap<>();
    }
}
