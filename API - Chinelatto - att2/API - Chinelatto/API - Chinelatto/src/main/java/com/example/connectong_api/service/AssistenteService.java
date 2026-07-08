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

    // Bairros/distritos conhecidos -> cidade. Usado para detectar a localizacao
    // MENCIONADA na conversa quando o doador cita um bairro (e nao a cidade).
    // Chaves SEM acento e minusculas (comparadas contra a mensagem normalizada).
    // Facil de estender: basta adicionar "bairro normalizado" -> "Cidade".
    private static final Map<String, String> BAIRROS_CIDADE = new LinkedHashMap<>();
    static {
        BAIRROS_CIDADE.put("barao geraldo", "Campinas");
        BAIRROS_CIDADE.put("cidade universitaria", "Campinas");
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

        // Dados reais lidos AO VIVO do banco a cada request (add/editar/excluir ONG
        // ou necessidade reflete sem redeploy; soft-deletadas/inativas nao entram).
        List<Ong> ongsAtivas = carregarOngsAtivas();
        List<Necessidade> necessidadesAbertas = carregarNecessidadesAbertas();

        // (1) LOCALIZACAO ADAPTAVEL: a cidade/bairro citado NA MENSAGEM vence a do
        // perfil. So se a mensagem nao disser nada usamos a cidade do body/perfil.
        String cidadeMensagem = detectarLocalizacaoNaMensagem(mensagem, ongsAtivas);
        String cidade = cidadeMensagem != null ? cidadeMensagem : resolverCidade(dto);

        // (2) GROUNDING QUERY-AWARE: filtra/prioriza pelos itens relevantes a
        // pergunta (localizacao detectada + categorias mencionadas) antes de cortar.
        Set<String> categoriasQuery = categoriasDetectadas(normalizar(mensagem));
        Contexto ctx = montarContexto(cidade, categoriasQuery, ongsAtivas, necessidadesAbertas);

        // (5) VISAO: se veio uma foto E ha provedor de visao, descreve os itens e
        // recomenda ONGs/categorias reais. Sem chave/sem imagem => fluxo de texto.
        boolean temImagem = temTexto(dto.getImagemBase64());
        if (temImagem && provedorIA.visaoDisponivel()) {
            AssistenteResponseDTO viaVisao = tentarVisao(dto, mensagem, cidade, ctx);
            if (viaVisao != null) {
                return ResponseEntity.ok(sanitizar(viaVisao));
            }
            // A chamada de visao falhou (timeout/429/rede): fallback amigavel.
            return ResponseEntity.ok(sanitizar(respostaVisaoFalhou(cidade, ctx)));
        }

        // 1) Tenta a IA (se ha chave e ela responde). Senao, cai no fallback.
        if (provedorIA.disponivel()) {
            AssistenteResponseDTO viaIa = tentarIa(dto, mensagem, cidade, ctx);
            if (viaIa != null) {
                return ResponseEntity.ok(sanitizar(viaIa));
            }
        }

        // 2) FALLBACK por regras (sempre funciona, sem chave).
        return ResponseEntity.ok(sanitizar(responderPorRegras(mensagem, cidade, ctx, "regras")));
    }

    // ================================================================
    // CAMINHO DA IA
    // ================================================================
    private AssistenteResponseDTO tentarIa(AssistenteRequestDTO dto, String mensagem,
                                           String cidade, Contexto ctx) {
        List<ProvedorIA.MensagemIA> mensagens = montarMensagens(dto, mensagem, cidade, ctx, false);

        Optional<String> saida = provedorIA.completar(mensagens);
        if (saida.isEmpty()) {
            return null; // IA indisponivel/falhou -> fallback
        }
        return interpretarSaidaIa(saida.get(), mensagem, cidade, ctx);
    }

    // (5) Caminho de VISAO: mesma montagem, mas system prompt orientado a foto e
    // a chamada anexa a imagem. Retorna null se a visao falhar (chamador mostra
    // um fallback amigavel). Reusa a validacao de ids e o parse de JSON do texto.
    private AssistenteResponseDTO tentarVisao(AssistenteRequestDTO dto, String mensagem,
                                              String cidade, Contexto ctx) {
        List<ProvedorIA.MensagemIA> mensagens = montarMensagens(dto, mensagem, cidade, ctx, true);

        Optional<String> saida = provedorIA.completarComImagem(mensagens, dto.getImagemBase64());
        if (saida.isEmpty()) {
            return null; // visao falhou -> chamador mostra fallback amigavel
        }
        return interpretarSaidaIa(saida.get(), mensagem, cidade, ctx);
    }

    // Monta system + historico + mensagem do usuario (comum a texto e visao).
    private List<ProvedorIA.MensagemIA> montarMensagens(AssistenteRequestDTO dto, String mensagem,
                                                        String cidade, Contexto ctx, boolean visao) {
        List<ProvedorIA.MensagemIA> mensagens = new ArrayList<>();
        mensagens.add(new ProvedorIA.MensagemIA("system", systemPrompt(cidade, ctx, visao)));

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
        return mensagens;
    }

    // Interpreta a saida da IA (JSON estruturado ou texto puro) -> resposta modo "ia".
    private AssistenteResponseDTO interpretarSaidaIa(String texto, String mensagem,
                                                     String cidade, Contexto ctx) {
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

    // System prompt: persona Dora + descreve o Connect ONG + injeta os dados reais.
    private String systemPrompt(String cidade, Contexto ctx, boolean visao) {
        StringBuilder sb = new StringBuilder();
        // (4) PERSONA: Dora, calorosa, acolhedora, brasileira, breve.
        sb.append("Voce e a Dora, a assistente de doacao do Connect ONG — uma ")
          .append("plataforma que conecta DOADORES a ONGs. As ONGs publicam ")
          .append("NECESSIDADES (o que precisam receber); o doador demonstra ")
          .append("interesse, forma um match, conversa no chat e combina a entrega, ")
          .append("ou doa dinheiro via PIX. Seu papel: ajudar o DOADOR a decidir ")
          .append("para quem/o que doar. Fale como brasileira, com tom caloroso, ")
          .append("acolhedor e breve; ao se apresentar, diga que voce e a Dora; ")
          .append("incentive a solidariedade com naturalidade, sem ser piegas.\n\n");

        if (visao) {
            sb.append("O doador enviou uma FOTO de itens que quer doar. Descreva ")
              .append("brevemente o que voce identifica na imagem e, com base nisso, ")
              .append("recomende ONGs/necessidades reais da lista abaixo (pela ")
              .append("categoria dos itens e pela localizacao). Se nao der para ")
              .append("identificar, peca gentilmente para descrever em texto.\n\n");
        }

        if (cidade != null && !cidade.isBlank()) {
            sb.append("Localizacao de referencia (a que o doador indicou ou a do ")
              .append("cadastro): ").append(cidade).append(".\n\n");
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
          .append("acima, citando o nome e a cidade. ")
          // (1) Localizacao vem da CONVERSA, nao do cadastro.
          .append("Use SEMPRE a localizacao que o doador DIZ na conversa (a cidade ou ")
          .append("o bairro que ele mencionar); NAO assuma a cidade do cadastro se ele ")
          .append("indicar outra. Priorize o que estiver perto dessa localizacao. Se ")
          .append("nada casar exatamente, sugira as opcoes mais proximas e explique. ")
          .append("Nao invente ONGs, dados de contato nem PIX.\n")
          // (3) PROIBIDO vazar ids/codigos na prosa.
          .append("NUNCA escreva ids, codigos ou trechos como \"[id=123]\" no campo ")
          .append("\"resposta\": na prosa cite APENAS o nome e a cidade da ONG/")
          .append("necessidade. Os ids vao SOMENTE dentro do array \"sugestoes\".\n")
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
        sb.append("Oi! Eu sou a Dora, a assistente de doacao do Connect ONG, e posso ")
          .append("te ajudar a doar. ")
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

    // (5) A chamada de VISAO falhou (timeout/429/rede): pede a descricao em texto,
    // mas ja mostra algumas necessidades reais para nao deixar o doador sem opcao.
    private AssistenteResponseDTO respostaVisaoFalhou(String cidade, Contexto ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nao consegui analisar a foto agora. Me conta em texto o que voce ")
          .append("tem para doar (roupas, alimentos, higiene, brinquedos, material ")
          .append("escolar...) que eu sugiro ONGs e necessidades reais. ")
          .append("Enquanto isso, veja algumas que estao precisando:");

        List<Sugestao> sugestoes = new ArrayList<>();
        List<Necessidade> destaque = new ArrayList<>(ctx.necessidades);
        ordenarNecessidades(destaque, cidade);
        for (Necessidade n : limitar(destaque, MAX_SUGESTOES)) {
            sugestoes.add(cardNecessidade(n));
        }
        return new AssistenteResponseDTO(sb.toString(), sugestoes, "regras");
    }

    /**
     * (3) SANITIZA a prosa: remove qualquer id/codigo vazado ("[id=123]", "id=123",
     * "id: 123", "(id 45)", "[id 7]") do campo `resposta`, em AMBOS os modos (IA e
     * regras). Os cards continuam intactos em `sugestoes`. Rede de seguranca alem
     * da instrucao no system prompt e da prosa sem ids do fallback.
     */
    private AssistenteResponseDTO sanitizar(AssistenteResponseDTO dto) {
        if (dto == null || dto.getResposta() == null) return dto;
        String r = dto.getResposta();
        // "[id=123]" / "(id 45)" / "[id: 7]" — com colchetes ou parenteses.
        r = r.replaceAll("(?i)[\\[(]\\s*id\\s*[:=]?\\s*\\d+\\s*[\\])]", "");
        // "id=123" / "id: 45" / "id 7" — soltos no meio do texto.
        r = r.replaceAll("(?i)\\bid\\s*[:=]\\s*\\d+", "");
        r = r.replaceAll("(?i)\\bid\\s+\\d+\\b", "");
        // Limpa residuos: colchetes/parenteses vazios, espacos duplos e espaco
        // antes de pontuacao.
        r = r.replaceAll("[\\[(]\\s*[\\])]", "")
             .replaceAll("\\s{2,}", " ")
             .replaceAll("\\s+([,.;:!?])", "$1")
             .trim();
        dto.setResposta(r);
        return dto;
    }

    // ================================================================
    // MONTAGEM DO CONTEXTO (dados reais)
    // ================================================================
    // ONGs ativas, lidas AO VIVO do banco (soft-deletadas nao entram).
    private List<Ong> carregarOngsAtivas() {
        List<Ong> ongs = new ArrayList<>();
        for (Ong o : ongRepository.findAll()) {
            if (o.getDataExclusao() == null) ongs.add(o);
        }
        return ongs;
    }

    // Necessidades abertas (status ABERTA/nulo) de ONG ativa, lidas AO VIVO.
    private List<Necessidade> carregarNecessidadesAbertas() {
        List<Necessidade> necessidades = new ArrayList<>();
        for (Necessidade n : necessidadeRepository.findAll()) {
            boolean aberta = n.getStatus() == null || n.getStatus().isBlank()
                    || "ABERTA".equalsIgnoreCase(n.getStatus());
            boolean ongAtiva = n.getOng() != null && n.getOng().getDataExclusao() == null;
            if (aberta && ongAtiva) necessidades.add(n);
        }
        return necessidades;
    }

    /**
     * (2) GROUNDING QUERY-AWARE E ESCALAVEL. Recebe TODAS as ONGs ativas e
     * necessidades abertas (ja lidas ao vivo) e escolhe as ~LIMITE mais RELEVANTES
     * a pergunta ANTES de cortar: pontua por (a) categoria(s) mencionadas e (b)
     * localizacao detectada; empates caem para urgente/verificada e recente/nota.
     * Assim, mesmo com centenas de ONGs, as relevantes ao que o doador perguntou
     * SEMPRE entram no teto; sobrando espaco, completa com urgentes/recentes.
     * Quando a pergunta nao traz categoria/cidade, degrada para a ordenacao antiga
     * (cidade -> urgente/verificada -> recente/nota).
     */
    private Contexto montarContexto(String cidade, Set<String> categoriasQuery,
                                    List<Ong> ongsAtivas, List<Necessidade> necessidadesAbertas) {
        Contexto ctx = new Contexto();
        String cid = normalizar(cidade);

        // Categorias que cada ONG pede, a partir de TODAS as necessidades abertas
        // (usado para pontuar a relevancia da ONG por categoria da pergunta).
        Map<Long, Set<String>> catsPorOngTodas = new LinkedHashMap<>();
        for (Necessidade n : necessidadesAbertas) {
            if (n.getOng() == null || !temTexto(n.getCategoria())) continue;
            catsPorOngTodas
                    .computeIfAbsent(n.getOng().getId(), k -> new LinkedHashSet<>())
                    .add(Categorias.normalizar(n.getCategoria()));
        }

        // Necessidades: relevancia (categoria+cidade) primeiro, depois urgente/recente.
        List<Necessidade> necessidades = new ArrayList<>(necessidadesAbertas);
        necessidades.sort(Comparator
                .comparingInt((Necessidade n) -> -pontuacaoNecessidade(n, cid, categoriasQuery))
                .thenComparing(n -> Boolean.TRUE.equals(n.getUrgente()) ? 0 : 1)
                .thenComparing(n -> n.getDataCriacao() != null
                        ? n.getDataCriacao() : LocalDateTime.MIN, Comparator.reverseOrder()));
        ctx.necessidades = limitar(necessidades, LIMITE_NECESSIDADES);
        for (Necessidade n : ctx.necessidades) ctx.necessidadePorId.put(n.getId(), n);

        // ONGs: relevancia (cidade+categoria) primeiro, depois verificada/nota.
        List<Ong> ongs = new ArrayList<>(ongsAtivas);
        ongs.sort(Comparator
                .comparingInt((Ong o) -> -pontuacaoOng(o, cid, categoriasQuery, catsPorOngTodas))
                .thenComparing(o -> o.getVerificada() ? 0 : 1)
                .thenComparing(Ong::getNotaMedia, Comparator.reverseOrder()));
        ctx.ongs = limitar(ongs, LIMITE_ONGS);
        for (Ong o : ctx.ongs) ctx.ongPorId.put(o.getId(), o);

        // Categorias por ONG (restrito ao recorte injetado) — para o system prompt.
        for (Ong o : ctx.ongs) {
            Set<String> cats = catsPorOngTodas.get(o.getId());
            if (cats != null && !cats.isEmpty()) {
                ctx.categoriasPorOng.put(o.getId(), new ArrayList<>(cats));
            }
        }
        return ctx;
    }

    // Relevancia da necessidade p/ a pergunta: +2 se casa alguma categoria citada,
    // +1 se e da localizacao detectada. (Sem categoria/cidade na pergunta => 0,
    // degradando para o desempate por urgente/recente = comportamento antigo.)
    private int pontuacaoNecessidade(Necessidade n, String cidadeNorm, Set<String> categoriasQuery) {
        int p = 0;
        if (!categoriasQuery.isEmpty() && categoriasQuery.stream()
                .anyMatch(c -> Categorias.iguais(n.getCategoria(), c))) {
            p += 2;
        }
        if (mesmaCidadeNec(n, cidadeNorm)) p += 1;
        return p;
    }

    // Relevancia da ONG p/ a pergunta: +2 se e da localizacao detectada, +1 se
    // costuma pedir alguma categoria citada.
    private int pontuacaoOng(Ong o, String cidadeNorm, Set<String> categoriasQuery,
                             Map<Long, Set<String>> catsPorOng) {
        int p = 0;
        if (temTexto(cidadeNorm) && cidadeNorm.equals(normalizar(o.getCidade()))) p += 2;
        if (!categoriasQuery.isEmpty()) {
            Set<String> cats = catsPorOng.get(o.getId());
            if (cats != null && cats.stream()
                    .anyMatch(c -> categoriasQuery.stream().anyMatch(q -> Categorias.iguais(c, q)))) {
                p += 1;
            }
        }
        return p;
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

    /**
     * (1) Detecta a localizacao MENCIONADA no texto da mensagem, que deve VENCER a
     * cidade do perfil/body. Estrategia:
     *   1) bairros/distritos conhecidos (mapa BAIRROS_CIDADE) -> cidade;
     *   2) nomes das cidades REAIS das ONGs ativas (case/acento-insensivel),
     *      preferindo o nome mais longo para evitar casar um pedaco.
     * Retorna a cidade detectada (forma de exibicao) ou null se nada for citado.
     */
    private String detectarLocalizacaoNaMensagem(String mensagem, List<Ong> ongsAtivas) {
        String norm = normalizar(mensagem);
        if (norm.isBlank()) return null;

        // 1) bairros conhecidos -> cidade.
        for (Map.Entry<String, String> e : BAIRROS_CIDADE.entrySet()) {
            if (norm.contains(e.getKey())) return e.getValue();
        }

        // 2) cidades reais das ONGs (do banco, ao vivo). Maior nome primeiro.
        List<Ong> ordenadas = new ArrayList<>(ongsAtivas);
        ordenadas.sort(Comparator.comparingInt(
                (Ong o) -> o.getCidade() == null ? 0 : o.getCidade().length()).reversed());
        for (Ong o : ordenadas) {
            String cidade = o.getCidade();
            if (!temTexto(cidade)) continue;
            String cidadeNorm = normalizar(cidade);
            // >= 3 chars e comparacao por palavra inteira (evita casar substrings).
            if (cidadeNorm.length() >= 3 && contemPalavra(norm, cidadeNorm)) {
                return cidade.trim();
            }
        }
        return null;
    }

    // true se `alvo` aparece em `texto` delimitado por inicio/fim ou nao-letras
    // (evita casar "sao" dentro de "sao paulo" errado, ou cidade dentro de palavra).
    private boolean contemPalavra(String texto, String alvo) {
        int from = 0;
        while (true) {
            int i = texto.indexOf(alvo, from);
            if (i < 0) return false;
            boolean antesOk = i == 0 || !Character.isLetterOrDigit(texto.charAt(i - 1));
            int fim = i + alvo.length();
            boolean depoisOk = fim >= texto.length() || !Character.isLetterOrDigit(texto.charAt(fim));
            if (antesOk && depoisOk) return true;
            from = i + 1;
        }
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
