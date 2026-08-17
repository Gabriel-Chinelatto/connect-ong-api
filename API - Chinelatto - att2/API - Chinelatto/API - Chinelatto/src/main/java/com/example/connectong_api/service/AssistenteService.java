package com.example.connectong_api.service;

import com.example.connectong_api.dto.AssistenteRequestDTO;
import com.example.connectong_api.dto.AssistenteResponseDTO;
import com.example.connectong_api.dto.AssistenteResponseDTO.Sugestao;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.model.DoacaoFinanceira;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.repository.DoacaoFinanceiraRepository;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.PrestacaoRepository;
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
    @Autowired private DoacaoFinanceiraRepository doacaoFinanceiraRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private PrestacaoRepository prestacaoRepository;
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
        // Campinas
        BAIRROS_CIDADE.put("barao geraldo", "Campinas");
        BAIRROS_CIDADE.put("cidade universitaria", "Campinas");
        BAIRROS_CIDADE.put("cambui", "Campinas");
        BAIRROS_CIDADE.put("taquaral", "Campinas");
        BAIRROS_CIDADE.put("nova campinas", "Campinas");
        BAIRROS_CIDADE.put("sousas", "Campinas");
        // Limeira
        BAIRROS_CIDADE.put("vila queiroz", "Limeira");
        BAIRROS_CIDADE.put("jardim gloria", "Limeira");
        BAIRROS_CIDADE.put("centro de limeira", "Limeira");
        BAIRROS_CIDADE.put("nova europa", "Limeira");
        BAIRROS_CIDADE.put("jardim aeroporto", "Limeira");
        BAIRROS_CIDADE.put("parque hipodromo", "Limeira");
        // Uberaba
        BAIRROS_CIDADE.put("fabricio", "Uberaba");
        BAIRROS_CIDADE.put("merces", "Uberaba");
        BAIRROS_CIDADE.put("santa maria", "Uberaba");
        BAIRROS_CIDADE.put("abadia", "Uberaba");
        BAIRROS_CIDADE.put("universitario", "Uberaba");
    }

    // Estados por extenso -> sigla (chaves normalizadas, ordem: nomes mais longos
    // primeiro, para "mato grosso do sul" nao ser roubado por "mato grosso").
    // "Para" fica FORA do mapa: normalizado colide com a preposicao "para" (tratado
    // por regex com preposicao em detectarLocalizacaoNaMensagem).
    private static final Map<String, String> ESTADO_SIGLA = new LinkedHashMap<>();
    static {
        ESTADO_SIGLA.put("rio grande do norte", "RN");
        ESTADO_SIGLA.put("rio grande do sul", "RS");
        ESTADO_SIGLA.put("mato grosso do sul", "MS");
        ESTADO_SIGLA.put("distrito federal", "DF");
        ESTADO_SIGLA.put("rio de janeiro", "RJ");
        ESTADO_SIGLA.put("santa catarina", "SC");
        ESTADO_SIGLA.put("espirito santo", "ES");
        ESTADO_SIGLA.put("mato grosso", "MT");
        ESTADO_SIGLA.put("minas gerais", "MG");
        ESTADO_SIGLA.put("sao paulo", "SP");
        ESTADO_SIGLA.put("pernambuco", "PE");
        ESTADO_SIGLA.put("tocantins", "TO");
        ESTADO_SIGLA.put("maranhao", "MA");
        ESTADO_SIGLA.put("rondonia", "RO");
        ESTADO_SIGLA.put("amazonas", "AM");
        ESTADO_SIGLA.put("roraima", "RR");
        ESTADO_SIGLA.put("alagoas", "AL");
        ESTADO_SIGLA.put("paraiba", "PB");
        ESTADO_SIGLA.put("parana", "PR");
        ESTADO_SIGLA.put("sergipe", "SE");
        ESTADO_SIGLA.put("goias", "GO");
        ESTADO_SIGLA.put("ceara", "CE");
        ESTADO_SIGLA.put("piaui", "PI");
        ESTADO_SIGLA.put("bahia", "BA");
        ESTADO_SIGLA.put("amapa", "AP");
        ESTADO_SIGLA.put("acre", "AC");
    }

    // Sigla -> nome apresentavel (para exibir "Rio de Janeiro" e nao "UF:RJ").
    private static final Map<String, String> SIGLA_ESTADO = new LinkedHashMap<>();
    static {
        SIGLA_ESTADO.put("AC", "Acre");            SIGLA_ESTADO.put("AL", "Alagoas");
        SIGLA_ESTADO.put("AP", "Amapa");           SIGLA_ESTADO.put("AM", "Amazonas");
        SIGLA_ESTADO.put("BA", "Bahia");           SIGLA_ESTADO.put("CE", "Ceara");
        SIGLA_ESTADO.put("DF", "Distrito Federal");SIGLA_ESTADO.put("ES", "Espirito Santo");
        SIGLA_ESTADO.put("GO", "Goias");           SIGLA_ESTADO.put("MA", "Maranhao");
        SIGLA_ESTADO.put("MT", "Mato Grosso");     SIGLA_ESTADO.put("MS", "Mato Grosso do Sul");
        SIGLA_ESTADO.put("MG", "Minas Gerais");    SIGLA_ESTADO.put("PA", "Para");
        SIGLA_ESTADO.put("PB", "Paraiba");         SIGLA_ESTADO.put("PR", "Parana");
        SIGLA_ESTADO.put("PE", "Pernambuco");      SIGLA_ESTADO.put("PI", "Piaui");
        SIGLA_ESTADO.put("RJ", "Rio de Janeiro");  SIGLA_ESTADO.put("RN", "Rio Grande do Norte");
        SIGLA_ESTADO.put("RS", "Rio Grande do Sul");SIGLA_ESTADO.put("RO", "Rondonia");
        SIGLA_ESTADO.put("RR", "Roraima");         SIGLA_ESTADO.put("SC", "Santa Catarina");
        SIGLA_ESTADO.put("SP", "Sao Paulo");       SIGLA_ESTADO.put("SE", "Sergipe");
        SIGLA_ESTADO.put("TO", "Tocantins");
    }

    // Sigla de UF so vale depois de preposicao ("em sp", "no rj") — varias siglas
    // sao palavras comuns do portugues (SE, MA, AM, PA, TO...).
    private static final java.util.regex.Pattern SIGLA_APOS_PREPOSICAO =
            java.util.regex.Pattern.compile("\\b(?:em|no|na|de|do|da|pra|para)\\s+([a-z]{2})\\b");
    // "Para" (estado) so com preposicao tipica de lugar, para nao casar a preposicao.
    private static final java.util.regex.Pattern ESTADO_PARA =
            java.util.regex.Pattern.compile("\\b(?:no|do|em) para\\b");

    // (1) Gatilhos de INTENCAO DE DOACAO (fallback por regras). So montamos cards
    // quando a mensagem casa uma categoria, cita uma ONG, ou contem um destes.
    // Substrings (casam em qualquer parte da palavra).
    private static final List<String> GATILHOS_DOACAO_SUBSTRING = List.of(
            "doar", "doacao", "doacoes", "doando", "quero doar", "posso doar",
            "onde doar", "o que doar", "para doar", "pra doar", "recomend",
            "necessidad", "instituic", "caridade", "voluntari", "filantrop",
            "arrecad", "desapeg", "quero ajudar", "ajudar quem", "como ajudar",
            "quem precisa", "abrigo", "orfanato", "asilo");
    // Palavras inteiras (evita casar dentro de outra palavra, ex.: "ong" em "longe").
    private static final List<String> GATILHOS_DOACAO_PALAVRA = List.of(
            "ong", "ongs", "perto", "proximo", "proxima", "proximos", "proximas",
            "doacao", "sugira", "sugere");

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

        // (4) USER-AWARE: se ha um doador AUTENTICADO, injeta um resumo CONCISO do
        // historico DELE (categorias, ONGs, cidades). Anonimo => sem bloco (a IA e
        // instruida a dizer que ainda nao tem historico). NUNCA dados de terceiros.
        anexarResumoDoador(ctx);

        // (5) VISAO: se veio uma foto E ha provedor de visao, descreve os itens e
        // recomenda ONGs/categorias reais. Sem chave/sem imagem => fluxo de texto.
        boolean temImagem = temTexto(dto.getImagemBase64());
        if (temImagem && provedorIA.visaoDisponivel()) {
            AssistenteResponseDTO viaVisao = tentarVisao(dto, mensagem, cidade, ctx);
            if (viaVisao != null) {
                return ResponseEntity.ok(finalizar(viaVisao, mensagem));
            }
            // A chamada de visao falhou (timeout/429/rede): fallback amigavel.
            return ResponseEntity.ok(finalizar(respostaVisaoFalhou(cidade, ctx), mensagem));
        }

        // 1) Tenta a IA (se ha chave e ela responde). Senao, cai no fallback.
        if (provedorIA.disponivel()) {
            AssistenteResponseDTO viaIa = tentarIa(dto, mensagem, cidade, ctx);
            if (viaIa != null) {
                return ResponseEntity.ok(finalizar(viaIa, mensagem));
            }
        }

        // 2) FALLBACK por regras (sempre funciona, sem chave).
        return ResponseEntity.ok(finalizar(
                responderPorRegras(mensagem, cidade, cidadeMensagem, ctx, "regras"), mensagem));
    }

    // ================================================================
    // SUGESTOES PROATIVAS (POST /assistente/sugestoes) — SEM mensagem de chat.
    // ================================================================
    /**
     * (6) Sugestoes PROATIVAS para o doador: sem mensagem de chat, usa o token (se
     * houver) para pegar a CIDADE e o HISTORICO de categorias do doador logado e
     * devolve as melhores necessidades/ONGs para ele — com uma frase curta ("Perto
     * de voce" / "Com base no que voce costuma doar"). Anonimo ou sem historico =>
     * panorama geral (necessidades urgentes/recentes). Reusa AssistenteResponseDTO
     * (o app reaproveita os mesmos cards). Sempre funciona sem chave (modo "regras").
     */
    public ResponseEntity<?> sugerirParaDoador() {
        List<Ong> ongsAtivas = carregarOngsAtivas();
        List<Necessidade> necessidadesAbertas = carregarNecessidadesAbertas();

        // Localizacao e categorias vem SO do perfil/historico do doador logado
        // (nao ha mensagem de chat aqui). Anonimo => ambos vazios.
        String cidade = cidadeDoUsuarioLogado();
        Set<String> categoriasDoador = categoriasDoDoadorLogado();

        // Prioriza por cidade + categorias que o doador costuma doar (grounding).
        Contexto ctx = montarContexto(cidade, categoriasDoador, ongsAtivas, necessidadesAbertas);

        // Cards: as necessidades ja vem ordenadas por relevancia (cidade+categoria);
        // se nao houver necessidades, cai para ONGs proximas.
        List<Sugestao> sugestoes = new ArrayList<>();
        for (Necessidade n : limitar(ctx.necessidades, MAX_SUGESTOES)) {
            sugestoes.add(cardNecessidade(n));
        }
        if (sugestoes.isEmpty()) {
            sugestoes = cardsOngsProximas(cidade, ctx);
        }

        // Frase curta conforme o sinal mais forte disponivel.
        String frase;
        String titulo;
        if (temTexto(cidade)) {
            frase = "Perto de voce, em " + cidade + ", estas doacoes estao precisando:";
            titulo = "Perto de voce";
        } else if (!categoriasDoador.isEmpty()) {
            frase = "Com base no que voce costuma doar, separei estas opcoes:";
            titulo = "Para voce";
        } else {
            frase = "Estas necessidades estao em destaque agora — comece por aqui:";
            titulo = "Em destaque";
        }
        if (sugestoes.isEmpty()) {
            frase = "Ainda nao ha necessidades abertas na plataforma. Volte em breve!";
        }

        AssistenteResponseDTO dto = new AssistenteResponseDTO(frase, sugestoes, titulo, "regras");
        return ResponseEntity.ok(sanitizar(dto));
    }

    // Cidade do perfil do doador logado (sem body de chat). null se anonimo/sem cidade.
    private String cidadeDoUsuarioLogado() {
        UsuarioAutenticado atual = security.atual();
        if (atual != null && atual.getId() != null) {
            Usuario u = usuarioRepository.findById(atual.getId()).orElse(null);
            if (u != null && u.getDataExclusao() == null && temTexto(u.getCidade())) {
                return u.getCidade().trim();
            }
        }
        return null;
    }

    // Categorias que o doador logado ja doou (matches CONCLUIDOS). Vazio p/ anonimo,
    // ONG logada ou sem historico. Usadas so para priorizar as sugestoes proativas.
    private Set<String> categoriasDoDoadorLogado() {
        Set<String> categorias = new LinkedHashSet<>();
        UsuarioAutenticado atual = security.atual();
        if (atual == null || atual.getId() == null) return categorias;
        if (atual.getTipo() != null && !"DOADOR".equalsIgnoreCase(atual.getTipo())) return categorias;
        for (Interesse i : interesseRepository.findByDoadorId(atual.getId())) {
            if (!"CONCLUIDO".equalsIgnoreCase(i.getStatus())) continue;
            Necessidade n = i.getNecessidade();
            if (n != null && temTexto(n.getCategoria())) {
                categorias.add(Categorias.normalizar(n.getCategoria()));
            }
        }
        return categorias;
    }

    // ================================================================
    // CAMINHO DA IA
    // ================================================================
    private AssistenteResponseDTO tentarIa(AssistenteRequestDTO dto, String mensagem,
                                           String cidade, Contexto ctx) {
        List<ProvedorIA.MensagemIA> mensagens = montarMensagens(dto, mensagem, cidade, ctx, false);

        // Dora (conversa): temperatura moderada (equilibra recomendar dados REAIS
        // com um tom acolhedor) e teto de tokens para a resposta + o JSON de cards.
        Optional<String> saida = provedorIA.completar(mensagens,
                ProvedorIA.OpcoesIA.de(0.4, 600));
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
        // Tenta interpretar como JSON {resposta, sugestoes:[{tipo,id}], titulo}.
        // (1) Respeitamos a decisao da IA: se ela devolveu sugestoes VAZIAS (conversa
        // geral / item nao doavel), o retorno vem vazio — NAO forcamos cards.
        AssistenteResponseDTO estruturada = parsearJsonIa(texto, ctx);
        if (estruturada != null) {
            return estruturada;
        }

        // A IA respondeu texto puro (nao-JSON): usa o texto como resposta. Só
        // derivamos cards por regras quando a mensagem tem intencao clara de doacao;
        // em conversa geral, sugestoes ficam VAZIAS. Modo continua "ia".
        List<Sugestao> sugestoes = new ArrayList<>();
        if (intencaoDoacao(normalizar(mensagem), ctx)) {
            sugestoes = responderPorRegras(mensagem, cidade,
                    detectarLocalizacaoNaMensagem(mensagem, ctx.ongs), ctx, "ia").getSugestoes();
        }
        return new AssistenteResponseDTO(texto.trim(), sugestoes, "ia");
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

            // (5) Titulo curto sugerido pela IA (opcional). Sanitizado no finalizar.
            String titulo = raiz.path("titulo").asText("").trim();

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
            return new AssistenteResponseDTO(resposta, sugestoes, titulo, "ia");
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
        // (4) PERSONA: Dora, conversacional, calorosa, com conhecimento geral.
        sb.append("Voce e a Dora, a assistente do Connect ONG — uma plataforma que ")
          .append("conecta DOADORES a ONGs. As ONGs publicam NECESSIDADES (o que ")
          .append("precisam receber); o doador demonstra interesse, forma um match, ")
          .append("conversa no chat e combina a entrega, ou doa dinheiro via PIX. ")
          .append("Voce e uma assistente CONVERSACIONAL simpatica, com CONHECIMENTO ")
          .append("GERAL: conversa normalmente sobre qualquer assunto, responde ")
          .append("perguntas gerais e bate-papo. Fale como brasileira, com tom ")
          .append("caloroso, acolhedor e breve; ao se apresentar, diga que voce e a ")
          .append("Dora; incentive a solidariedade com naturalidade, sem ser piegas.\n\n");

        // (1) MODO CONVERSA: cards SO quando o usuario esta pedindo ajuda com doacao.
        sb.append("QUANDO INCLUIR CARDS (o array \"sugestoes\"): APENAS quando o ")
          .append("usuario estiver pedindo recomendacao de doacao — onde/o que doar, ")
          .append("achar ONGs, para quem doar tal item, o que uma ONG precisa. Nesse ")
          .append("caso, sugira ONGs/necessidades reais da lista abaixo. Para ")
          .append("CONVERSA GERAL ou off-topic (ex.: 'qual a capital da Franca?', ")
          .append("'quem ganhou a copa?', bate-papo), o array \"sugestoes\" deve vir ")
          .append("VAZIO e voce apenas conversa; no maximo, no fim, ofereca ajuda com ")
          .append("doacao DE LEVE, sem despejar cards.\n\n");

        if (visao) {
            // (2) VISAO mais esperta: descreve e so recomenda se for item doavel.
            sb.append("O doador enviou uma FOTO. Descreva brevemente o que voce ve. ")
              .append("Se for item(ns) DOAVEL(is) — roupas, alimentos, higiene, ")
              .append("brinquedos, livros, material escolar, utensilios domesticos, ")
              .append("moveis, eletronicos etc. —, identifique a categoria e recomende ")
              .append("ONGs/necessidades reais da lista abaixo (por categoria e ")
              .append("localizacao), preenchendo \"sugestoes\". Se NAO for algo doavel ")
              .append("(pessoa, selfie, paisagem, documento, animal de estimacao), ")
              .append("responda com gentileza (ex.: 'essa foto nao parece um item para ")
              .append("doacao; me mostra o que voce quer doar?') e deixe \"sugestoes\" ")
              .append("VAZIO. Nunca trave nem invente.\n\n");
        }

        // (4) USER-AWARE: resumo do doador logado, se houver.
        if (temTexto(ctx.resumoUsuario)) {
            sb.append(ctx.resumoUsuario).append("\n\n");
        }

        if (cidade != null && !cidade.isBlank()) {
            sb.append("Localizacao de referencia (a que o doador indicou ou a do ")
              .append("cadastro): ").append(exibirLugar(cidade)).append(".\n\n");
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
          .append("acolhedora e objetiva. Ao recomendar doacao, use apenas ONGs/")
          .append("necessidades da lista acima, citando o nome e a cidade. ")
          // (1) Localizacao vem da CONVERSA, nao do cadastro.
          .append("Use SEMPRE a localizacao que o doador DIZ na conversa (a cidade ou ")
          .append("o bairro que ele mencionar); NAO assuma a cidade do cadastro se ele ")
          .append("indicar outra. ")
          // (3) Geografia com o proprio conhecimento: deduz cidade a partir do bairro.
          .append("Se ele citar um BAIRRO, use seu conhecimento geografico para deduzir ")
          .append("a cidade (ex.: 'esse bairro fica em Limeira') e recomende ONGs dessa ")
          .append("cidade. Priorize o que estiver perto dessa localizacao. Se NAO houver ")
          .append("ONG cadastrada na cidade deduzida, diga isso com franqueza e sugira as ")
          .append("mais proximas ou opcoes gerais. Nao invente ONGs, contato nem PIX.\n")
          // (4) Personalizacao + privacidade.
          .append("Se houver um resumo do doador logado acima, use-o para personalizar ")
          .append("('vejo que voce costuma doar X para ONGs em Y...') e para responder ")
          .append("'quem sou eu?' ou 'com base no que ja doei, onde posso doar?'. Se NAO ")
          .append("houver resumo (visitante sem login/sem historico), diga com gentileza ")
          .append("que ainda nao tem o historico dele e ofereca ajuda para comecar. NUNCA ")
          .append("exponha dados de OUTROS usuarios.\n")
          // (3-old) PROIBIDO vazar ids/codigos na prosa.
          .append("NUNCA escreva ids, codigos ou trechos como \"[id=123]\" no campo ")
          .append("\"resposta\" nem no \"titulo\": na prosa cite APENAS o nome e a ")
          .append("cidade da ONG/necessidade. Os ids vao SOMENTE dentro de \"sugestoes\".\n")
          .append("Responda EXCLUSIVAMENTE com um JSON valido, sem texto fora dele, no ")
          .append("formato: {\"resposta\":\"seu texto\",\"sugestoes\":[{\"tipo\":")
          .append("\"NECESSIDADE\",\"id\":123},{\"tipo\":\"ONG\",\"id\":45}],")
          .append("\"titulo\":\"assunto em 2-4 palavras\"}. ")
          .append("O \"titulo\" resume o assunto da conversa em 2 a 4 palavras (PT-BR, ")
          .append("sem ids nem emoji), para nomear o chat. Inclua em \"sugestoes\" os ids ")
          .append("reais das opcoes citadas (no maximo ").append(MAX_SUGESTOES)
          .append("); em CONVERSA GERAL ou item nao doavel, use \"sugestoes\":[] (lista ")
          .append("vazia).");
        return sb.toString();
    }

    // ================================================================
    // FALLBACK POR REGRAS
    // ================================================================
    private AssistenteResponseDTO responderPorRegras(String mensagem, String cidade,
                                                     String cidadeMensagem,
                                                     Contexto ctx, String modo) {
        String norm = normalizar(mensagem);

        // (1) MODO CONVERSA no fallback: se NAO ha intencao clara de doacao, responde
        // curto e conversacional, SEM cards (nao despeja ONGs em pergunta geral).
        // EXCECAO: mensagem que se APRESENTA com um lugar ("sou de campinas",
        // "moro no rio") e continuacao util da conversa de doacao — responde com
        // ONGs de la. Exige o marcador de localizacao: so o nome da cidade solto
        // numa pergunta geral ("qual a capital da Franca?" casa com Franca-SP)
        // NAO pode virar despejo de cards.
        if (!intencaoDoacao(norm, ctx)) {
            boolean seApresentaComLugar = norm.contains("sou de") || norm.contains("moro")
                    || norm.contains("estou em") || norm.contains("estou no")
                    || norm.contains("estou na") || norm.contains("to em")
                    || norm.contains("to no") || norm.contains("to na")
                    || norm.contains("estiver") || norm.contains("minha cidade")
                    || norm.contains("aqui em") || norm.contains("venho de");
            if (cidadeMensagem != null && seApresentaComLugar) {
                return respostaPorProximidade(cidade, ctx, modo);
            }
            return respostaConversacional(modo);
        }

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

        // (B) Intencao de proximidade/cidade (sem categoria). Lugar citado NA
        // MENSAGEM ("onde doar no rio de janeiro?") tambem conta como proximidade.
        boolean querPerto = cidadeMensagem != null
                || norm.contains("perto") || norm.contains("proxim")
                || norm.contains("por perto") || norm.contains("minha cidade")
                || norm.contains("aqui em") || norm.contains("na regiao")
                || (temTexto(cidade) && norm.contains(normalizar(parteCidade(cidade))));
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
            if (temTexto(cidade)) sb.append(" perto de ").append(exibirLugar(cidade));
            sb.append(". Veja se alguma combina com o que voce tem para doar:");
            for (Necessidade n : limitar(casam, MAX_SUGESTOES)) {
                sugestoes.add(cardNecessidade(n));
            }
        } else {
            // Sem match exato: oferece ONGs proximas / necessidades recentes.
            sb.append("No momento nao achei uma necessidade aberta de ").append(catTexto);
            if (temTexto(cidade)) sb.append(" em ").append(exibirLugar(cidade));
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
                    ? "Perto de " + exibirLugar(cidade) + ", estas ONGs estao recebendo doacoes:"
                    : "Estas ONGs estao ativas e recebendo doacoes:");
        } else {
            sb.append("Ainda nao encontrei ONGs cadastradas");
            if (temTexto(cidade)) sb.append(" em ").append(exibirLugar(cidade));
            sb.append(". Enquanto isso, veja necessidades abertas em outras cidades:");
            for (Necessidade n : limitar(ctx.necessidades, MAX_SUGESTOES)) {
                sugestoes.add(cardNecessidade(n));
            }
        }
        return new AssistenteResponseDTO(sb.toString(), sugestoes, modo);
    }

    private AssistenteResponseDTO respostaNecessidadesDaOng(Ong ong, Contexto ctx, String modo) {
        List<Necessidade> daOng = new ArrayList<>();
        List<Necessidade> universo = ctx.todasNecessidades.isEmpty()
                ? ctx.necessidades : ctx.todasNecessidades;
        for (Necessidade n : universo) {
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

    // (1) Ha intencao clara de DOACAO na mensagem? (categoria reconhecida, ONG
    // citada, ou algum gatilho de doar/ong/perto...). Em conversa geral, false.
    private boolean intencaoDoacao(String norm, Contexto ctx) {
        if (norm == null || norm.isBlank()) return false;
        if (!categoriasDetectadas(norm).isEmpty()) return true;
        if (ongCitadaNaMensagem(norm, ctx) != null) return true;
        for (String g : GATILHOS_DOACAO_SUBSTRING) {
            if (norm.contains(g)) return true;
        }
        for (String g : GATILHOS_DOACAO_PALAVRA) {
            if (contemPalavra(norm, g)) return true;
        }
        return false;
    }

    // (1) Conversa geral no fallback (sem chave de IA): resposta curta e simpatica,
    // SEM cards. O fallback nao tem conhecimento geral, entao conversa de leve e
    // reencaminha para o que ela sabe fazer: ajudar a doar.
    private AssistenteResponseDTO respostaConversacional(String modo) {
        String txt = "Oi! Eu sou a Dora, do Connect ONG. Adoro bater papo, mas sobre "
                + "esse assunto eu nao vou saber responder com detalhe. O que eu faco "
                + "de melhor e te ajudar a doar: e so me dizer o que voce tem para doar "
                + "(roupas, alimentos, higiene, brinquedos, material escolar...) ou de "
                + "que cidade voce e, que eu sugiro ONGs e necessidades reais.";
        return new AssistenteResponseDTO(txt, new ArrayList<>(), modo);
    }

    /**
     * (4) USER-AWARE. Se ha um doador AUTENTICADO, monta um resumo CONCISO do
     * historico DELE (categorias que ja doou, ONGs que ajudou, cidades, contagens)
     * a partir de dados REAIS: matches CONCLUIDOS (Interesse), doacoes PIX e
     * prestacoes recebidas. Injeta no Contexto para o system prompt personalizar.
     * NUNCA le dados de outro usuario (tudo filtrado pelo id do token). Anonimo ou
     * sem historico => nao injeta bloco (a IA e instruida a lidar com isso).
     */
    private void anexarResumoDoador(Contexto ctx) {
        UsuarioAutenticado atual = security.atual();
        if (atual == null || atual.getId() == null) return;
        // So o DOADOR tem historico de doacao; ONG logada nao se aplica.
        if (atual.getTipo() != null && !"DOADOR".equalsIgnoreCase(atual.getTipo())) return;

        Long doadorId = atual.getId();
        Usuario u = usuarioRepository.findById(doadorId).orElse(null);
        if (u != null && u.getDataExclusao() != null) return; // conta excluida

        Set<String> categorias = new LinkedHashSet<>();
        Set<String> ongsAjudadas = new LinkedHashSet<>();
        Set<String> cidades = new LinkedHashSet<>();
        int matchesConcluidos = 0;

        // Matches CONCLUIDOS (doacao de item confirmada pela ONG) — sao a fonte mais
        // rica: categoria da necessidade, ONG e cidade.
        for (Interesse i : interesseRepository.findByDoadorId(doadorId)) {
            if (!"CONCLUIDO".equalsIgnoreCase(i.getStatus())) continue;
            matchesConcluidos++;
            Necessidade n = i.getNecessidade();
            if (n == null) continue;
            if (temTexto(n.getCategoria())) categorias.add(Categorias.normalizar(n.getCategoria()));
            if (n.getOng() != null) {
                if (temTexto(n.getOng().getNome())) ongsAjudadas.add(n.getOng().getNome().trim());
                if (temTexto(n.getOng().getCidade())) cidades.add(n.getOng().getCidade().trim());
            }
        }

        // Doacoes financeiras (PIX): adicionam ONGs ajudadas.
        List<DoacaoFinanceira> pix = doacaoFinanceiraRepository
                .findByDoadorIdOrderByDataCriacaoDesc(doadorId);
        for (DoacaoFinanceira d : pix) {
            if (temTexto(d.getOngNome())) ongsAjudadas.add(d.getOngNome().trim());
        }
        int doacoesPix = pix.size();
        long prestacoesRecebidas = prestacaoRepository
                .findByInteresseDoadorIdOrderByDataCriacaoDesc(doadorId).size();

        boolean semHistorico = matchesConcluidos == 0 && doacoesPix == 0
                && ongsAjudadas.isEmpty() && categorias.isEmpty();

        StringBuilder sb = new StringBuilder();
        sb.append("Sobre o DOADOR logado (use para personalizar; e o proprio usuario, ")
          .append("NUNCA exponha dados de terceiros):");
        if (u != null && temTexto(u.getNome())) sb.append("\n- Nome: ").append(u.getNome().trim());
        if (u != null && temTexto(u.getCidade())) sb.append("\n- Cidade do cadastro: ").append(u.getCidade().trim());
        if (!categorias.isEmpty()) {
            sb.append("\n- Ja doou itens nas categorias: ")
              .append(String.join(", ", limitar(new ArrayList<>(categorias), 6)));
        }
        if (!ongsAjudadas.isEmpty()) {
            sb.append("\n- Ja ajudou as ONGs: ")
              .append(String.join(", ", limitar(new ArrayList<>(ongsAjudadas), 6)));
        }
        if (!cidades.isEmpty()) {
            sb.append("\n- Cidades onde ja doou: ")
              .append(String.join(", ", limitar(new ArrayList<>(cidades), 4)));
        }
        sb.append("\n- Matches concluidos: ").append(matchesConcluidos)
          .append("; doacoes via PIX: ").append(doacoesPix)
          .append("; prestacoes de contas recebidas: ").append(prestacoesRecebidas).append(".");
        if (semHistorico) {
            sb.append("\n- Ele ainda NAO tem historico de doacao. Se ele perguntar sobre ")
              .append("si, diga isso com gentileza e ofereca ajuda para comecar a doar.");
        }
        ctx.resumoUsuario = sb.toString();
    }

    // (5) Deriva um titulo simples da 1a mensagem quando a IA nao mandou um (ou no
    // fallback): capitaliza e corta em ~4 palavras / 40 chars, sem ids.
    private String tituloDe(String mensagem) {
        String limpa = limparTitulo(mensagem);
        if (limpa == null || limpa.isBlank()) return "Conversa";
        String[] palavras = limpa.split("\\s+");
        int n = Math.min(palavras.length, 4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append(palavras[i]);
        }
        String t = sb.toString().trim();
        if (t.isEmpty()) return "Conversa";
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    // (5) SANITIZA o titulo: remove ids/codigos, aspas e quebras, colapsa espacos e
    // corta em 40 chars. Retorna null quando entra nulo.
    private String limparTitulo(String t) {
        if (t == null) return null;
        String s = t.replaceAll("(?i)[\\[(]?\\s*id\\s*[:=]?\\s*\\d+\\s*[\\])]?", " ")
                    .replaceAll("[\\r\\n\"]", " ")
                    .replaceAll("[\\[\\](){}]", " ")
                    .replaceAll("\\s{2,}", " ")
                    .trim();
        if (s.length() > 40) s = s.substring(0, 40).trim();
        return s;
    }

    // (3+5) Passo final comum: sanitiza a prosa (remove ids) e GARANTE um titulo
    // (usa o da IA se veio limpo; senao deriva da mensagem do usuario).
    private AssistenteResponseDTO finalizar(AssistenteResponseDTO dto, String mensagem) {
        sanitizar(dto);
        if (dto != null) {
            String t = limparTitulo(dto.getTitulo());
            if (t == null || t.isBlank()) t = tituloDe(mensagem);
            dto.setTitulo(t);
        }
        return dto;
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
        ctx.todasOngs = ongsAtivas;
        ctx.todasNecessidades = necessidadesAbertas;

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
                .comparingInt((Necessidade n) -> -pontuacaoNecessidade(n, cidade, categoriasQuery))
                .thenComparing(n -> Boolean.TRUE.equals(n.getUrgente()) ? 0 : 1)
                .thenComparing(n -> n.getDataCriacao() != null
                        ? n.getDataCriacao() : LocalDateTime.MIN, Comparator.reverseOrder()));
        ctx.necessidades = limitar(necessidades, LIMITE_NECESSIDADES);
        for (Necessidade n : ctx.necessidades) ctx.necessidadePorId.put(n.getId(), n);

        // ONGs: relevancia (cidade+categoria) primeiro, depois verificada/nota.
        List<Ong> ongs = new ArrayList<>(ongsAtivas);
        ongs.sort(Comparator
                .comparingInt((Ong o) -> -pontuacaoOng(o, cidade, categoriasQuery, catsPorOngTodas))
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
    private int pontuacaoNecessidade(Necessidade n, String cidade, Set<String> categoriasQuery) {
        int p = 0;
        if (!categoriasQuery.isEmpty() && categoriasQuery.stream()
                .anyMatch(c -> Categorias.iguais(n.getCategoria(), c))) {
            p += 2;
        }
        if (mesmaCidadeNec(n, cidade)) p += 1;
        return p;
    }

    // Relevancia da ONG p/ a pergunta: +2 se e da localizacao detectada, +1 se
    // costuma pedir alguma categoria citada.
    private int pontuacaoOng(Ong o, String cidade, Set<String> categoriasQuery,
                             Map<Long, Set<String>> catsPorOng) {
        int p = 0;
        if (mesmoLugar(o.getCidade(), cidade)) p += 2;
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
        lista.sort(Comparator
                .comparing((Necessidade n) -> mesmaCidadeNec(n, cidade) ? 0 : 1)
                .thenComparing(n -> Boolean.TRUE.equals(n.getUrgente()) ? 0 : 1)
                .thenComparing(n -> n.getDataCriacao() != null
                        ? n.getDataCriacao() : LocalDateTime.MIN, Comparator.reverseOrder()));
    }

    private boolean mesmaCidadeNec(Necessidade n, String cidade) {
        return n.getOng() != null && mesmoLugar(n.getOng().getCidade(), cidade);
    }

    private List<Sugestao> cardsOngsProximas(String cidade, Contexto ctx) {
        List<Sugestao> proximas = new ArrayList<>();
        // Primeiro as do lugar pedido (cidade ou estado), quando informado.
        if (temTexto(cidade)) {
            for (Ong o : ctx.ongs) {
                if (mesmoLugar(o.getCidade(), cidade)) proximas.add(cardOng(o));
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

        // 2) cidades reais das ONGs (do banco, ao vivo). O campo guarda
        //    "Cidade - UF", mas a mensagem diz so a cidade ("moro em campinas"):
        //    comparamos a PARTE-CIDADE. Maior nome primeiro (evita "sao jose"
        //    roubar de "sao jose dos campos").
        List<Ong> ordenadas = new ArrayList<>(ongsAtivas);
        ordenadas.sort(Comparator.comparingInt(
                (Ong o) -> parteCidade(o.getCidade()).length()).reversed());
        for (Ong o : ordenadas) {
            String soCidade = parteCidade(o.getCidade());
            String cidadeNorm = normalizar(soCidade);
            // >= 3 chars e comparacao por palavra inteira (evita casar substrings).
            if (cidadeNorm.length() >= 3 && contemPalavra(norm, cidadeNorm)) {
                return o.getCidade().trim();
            }
        }

        // 3) ESTADO por extenso ("se eu estiver no parana...") -> filtro por UF.
        for (Map.Entry<String, String> e : ESTADO_SIGLA.entrySet()) {
            if (contemPalavra(norm, e.getKey())) return "UF:" + e.getValue();
        }
        if (ESTADO_PARA.matcher(norm).find()) return "UF:PA";

        // 4) Sigla de UF depois de preposicao ("em sp", "no rj").
        java.util.regex.Matcher m = SIGLA_APOS_PREPOSICAO.matcher(norm);
        while (m.find()) {
            String sigla = m.group(1).toUpperCase();
            if (SIGLA_ESTADO.containsKey(sigla)) return "UF:" + sigla;
        }
        return null;
    }

    // ----------------------------------------------------------------
    // O campo unico `cidade` das ONGs guarda "Cidade - UF" (nao existe coluna
    // estado). Estes helpers separam as partes para comparar LUGAR sem depender
    // do formato — era exatamente isso que fazia "rio de janeiro" nunca casar
    // com "Rio de Janeiro - RJ" no modo por regras.
    // ----------------------------------------------------------------
    private static String parteCidade(String campo) {
        if (campo == null) return "";
        String t = campo.trim();
        int i = t.lastIndexOf(" - ");
        if (i > 0 && t.length() - (i + 3) == 2) return t.substring(0, i).trim();
        return t;
    }

    private static String parteUf(String campo) {
        if (campo == null) return "";
        String t = campo.trim();
        int i = t.lastIndexOf(" - ");
        if (i > 0 && t.length() - (i + 3) == 2) return t.substring(i + 3).toUpperCase();
        return "";
    }

    // `lugar` e o que foi detectado/resolvido: "Campinas - SP", "Limeira" ou o
    // sentinel "UF:RJ" (estado). Casa cidade com cidade (ignorando a UF do campo)
    // ou o campo inteiro com a UF pedida.
    private boolean mesmoLugar(String cidadeCampoOng, String lugar) {
        if (!temTexto(lugar) || !temTexto(cidadeCampoOng)) return false;
        if (lugar.startsWith("UF:")) {
            return lugar.substring(3).equalsIgnoreCase(parteUf(cidadeCampoOng));
        }
        return normalizar(parteCidade(lugar)).equals(normalizar(parteCidade(cidadeCampoOng)));
    }

    // Nome apresentavel do lugar (o sentinel "UF:RJ" vira "Rio de Janeiro").
    private static String exibirLugar(String lugar) {
        if (lugar == null) return null;
        if (lugar.startsWith("UF:")) {
            String nome = SIGLA_ESTADO.get(lugar.substring(3));
            return nome != null ? nome : lugar.substring(3);
        }
        return lugar;
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
        List<Ong> universo = ctx.todasOngs.isEmpty() ? ctx.ongs : ctx.todasOngs;
        for (Ong o : universo) {
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
        // Listas COMPLETAS (sem o corte de relevancia): usadas para achar uma ONG
        // citada pelo NOME e as necessidades dela — com 2.000 ONGs, a citada
        // quase nunca esta no top-20 do recorte.
        List<Ong> todasOngs = new ArrayList<>();
        List<Necessidade> todasNecessidades = new ArrayList<>();
        Map<Long, Ong> ongPorId = new LinkedHashMap<>();
        Map<Long, Necessidade> necessidadePorId = new LinkedHashMap<>();
        Map<Long, List<String>> categoriasPorOng = new LinkedHashMap<>();
        // (4) Resumo do doador autenticado (null p/ anonimo) — injetado no prompt.
        String resumoUsuario;
    }
}
