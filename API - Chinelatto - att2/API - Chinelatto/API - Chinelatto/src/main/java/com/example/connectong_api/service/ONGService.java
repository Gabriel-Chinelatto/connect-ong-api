package com.example.connectong_api.service;

import com.example.connectong_api.dto.OngRegistroDTO;
import com.example.connectong_api.dto.OngResponseDTO;
import com.example.connectong_api.dto.OngUpdateDTO;
import com.example.connectong_api.dto.PerfilPublicoOngDTO;
import com.example.connectong_api.dto.UsuarioResponseDTO;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.OngFoto;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.OngFotoRepository;
import com.example.connectong_api.repository.PreferenciaRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.security.UsuarioAutenticado;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gerencia as ONGs: CRUD, verificacao (selo) e o perfil publico agregado que o
 * doador ve (necessidades, campanhas, avaliacoes, prestacoes e score de
 * transparencia). No cadastro cria, de forma atomica, o perfil da ONG e a conta
 * de login (Usuario tipo ONG) ja vinculados, com a senha em hash BCrypt e tokens JWT.
 */
@Service
public class ONGService {

    @Autowired
    private ONGRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private NecessidadeService necessidadeService;

    @Autowired
    private CampanhaService campanhaService;

    @Autowired
    private AvaliacaoService avaliacaoService;

    @Autowired
    private PrestacaoService prestacaoService;

    @Autowired
    private OngFotoRepository ongFotoRepository;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private TransparenciaService transparenciaService;

    @Autowired
    private SecurityUtils security;

    @Autowired
    private BloqueioService bloqueioService;

    @Autowired
    private PreferenciaRepository preferenciaRepository;

    // =========================
    // PERFIL PUBLICO (agrega tudo que o doador ve na pagina da ONG)
    // =========================
    public ResponseEntity<?> perfilPublico(Long id) {
        Ong ong = repository.findById(id).orElse(null);
        // ONG inexistente OU excluida (soft-delete) -> 404.
        if (ong == null || ong.getDataExclusao() != null) {
            return ResponseEntity.notFound().build();
        }

        // BLOQUEIO: doador bloqueado por esta ONG recebe 200 com o corpo MINIMO
        // (id, nome, bloqueado) — sem o resto do perfil. Requisicao anonima
        // segue normal (o endpoint e publico, destino do link compartilhavel).
        UsuarioAutenticado quem = security.atual();
        if (quem != null && "DOADOR".equals(quem.getTipo())
                && bloqueioService.bloqueado(id, quem.getId())) {
            Map<String, Object> minimo = new LinkedHashMap<>();
            minimo.put("id", ong.getId());
            minimo.put("nome", ong.getNome());
            minimo.put("bloqueado", true);
            return ResponseEntity.ok(minimo);
        }

        var transp = transparenciaService.calcular(ong);

        PerfilPublicoOngDTO dto = new PerfilPublicoOngDTO(
                ong,
                necessidadeService.listar(id, null, null),
                campanhaService.listar(id, false, null),
                avaliacaoService.listar(id),
                prestacaoService.listarPorOng(id),
                transp.getScore(),
                transp.getNivel());

        // Perfil rico: fotos do local (max 5).
        dto.setFotosLocal(fotosLocal(id));

        // Streak: diasNoTopo so aparece se esta ONG e a ATUAL #1 do ranking
        // (top1Desde aberto); ultimoReinadoDias ja vai no construtor.
        if (ong.getTop1Desde() != null) {
            dto.setDiasNoTopo(transparenciaService.diasNoTopo(ong.getTop1Desde()));
        }

        // PRIVACIDADE REAL: os toggles mostrarEmail/mostrarTelefone (Preferencia
        // da conta-ONG dona) valem no perfil publico — desligado = campo omitido.
        aplicarPrivacidade(dto, id);

        return ResponseEntity.ok(dto);
    }

    /**
     * Aplica os toggles de privacidade da conta-ONG dona ao perfil publico.
     * Sem registro de Preferencia (usuario nunca abriu as configuracoes) valem
     * os MESMOS defaults de Preferencia.padrao(): email OCULTO, telefone
     * VISIVEL — o perfil publico nunca contradiz a tela de configuracoes.
     * ONG legada sem conta de login vinculada: mantem o perfil como esta.
     */
    private void aplicarPrivacidade(PerfilPublicoOngDTO dto, Long ongId) {
        boolean[] p = privacidadeDaOng(ongId);
        if (!p[0]) {
            dto.setEmail(null);
        }
        if (!p[1]) {
            dto.setTelefone(null);
        }
    }

    /**
     * Resolve os toggles de contato da conta-ONG dona: [exibirEmail, exibirTelefone].
     * Sem conta vinculada (ONG legada) OU sem registro de Preferencia recai nos
     * MESMOS defaults de Preferencia.padrao(): email OCULTO, telefone VISIVEL.
     * Campo null no registro (coluna criada depois) tambem recai nesses defaults.
     * Unico ponto de verdade da privacidade de contato — usado no perfil publico,
     * na listagem GET /ongs e no detalhe GET /ongs/{id}, para nunca se contradizerem.
     */
    private boolean[] privacidadeDaOng(Long ongId) {
        Usuario conta = usuarioRepository.findByOngId(ongId).orElse(null);
        if (conta == null) {
            // ONG legada sem conta de login: aplica os defaults seguros mesmo assim
            // (email oculto, telefone visivel) em vez de expor tudo.
            return new boolean[] { false, true };
        }
        Preferencia prefs = preferenciaRepository.findByUsuarioId(conta.getId())
                .orElseGet(() -> Preferencia.padrao(conta.getId()));

        boolean exibirEmail = prefs.getMostrarEmail() != null
                ? prefs.getMostrarEmail() : false;
        boolean exibirTelefone = prefs.getMostrarTelefone() != null
                ? prefs.getMostrarTelefone() : true;
        return new boolean[] { exibirEmail, exibirTelefone };
    }

    // =========================
    // CADASTRO (cria o perfil da ONG + a conta de login, ja vinculados)
    // =========================
    public ResponseEntity<?> registrar(OngRegistroDTO dto) {

        // Mesmo limite por IP dos demais cadastros publicos (mitigacao de
        // enumeracao de email: a mensagem "Email já cadastrado" foi mantida
        // pela UX da feira; o rate limiting inviabiliza varredura em massa).
        if (rateLimitService.excedeuSolicitacoes("cadastro")) {
            return RateLimitService.resposta429();
        }

        if (dto.getNome() == null || dto.getNome().isBlank()) {
            return erro("Nome da ONG é obrigatório");
        }
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            return erro("Email é obrigatório");
        }
        if (dto.getSenha() == null || dto.getSenha().isBlank()) {
            return erro("Senha é obrigatória");
        }

        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            return erro("Email já cadastrado");
        }

        // 1) cria o perfil da ONG. Campos opcionais (telefone/cidade/descricao/
        // cnpj) viram "" quando ausentes: evita null em colunas NOT NULL do banco
        // e mantem a resposta consistente (o cliente exibe string, nunca null).
        Ong ong = new Ong(
                dto.getNome(),
                dto.getEmail(),
                naoNulo(dto.getTelefone()),
                naoNulo(dto.getCidade()),
                naoNulo(dto.getDescricao())
        );
        ong.setCnpj(naoNulo(dto.getCnpj()));
        Ong ongSalva = repository.save(ong);

        // 2) cria a conta de login (Usuario tipo ONG) vinculada ao perfil
        Usuario usuario = new Usuario();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));
        usuario.setTipo("ONG");
        usuario.setOngId(ongSalva.getId());
        Usuario usuarioSalvo = usuarioRepository.save(usuario);

        UsuarioResponseDTO resposta = new UsuarioResponseDTO(
                usuarioSalvo.getId(),
                usuarioSalvo.getNome(),
                usuarioSalvo.getEmail(),
                usuarioSalvo.getTipo(),
                usuarioSalvo.getOngId()
        );

        resposta.setAccessToken(jwtService.gerarAccessToken(usuarioSalvo));
        resposta.setRefreshToken(jwtService.gerarRefreshToken(usuarioSalvo));

        auditService.registrar("CADASTRO_ONG", usuarioSalvo.getId(),
                "Nova ONG cadastrada: " + ongSalva.getNome()
                        + " (ongId=" + ongSalva.getId() + ")");

        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    // =========================
    // LISTAR
    // =========================
    public List<OngResponseDTO> listar(
            String nome
    ) {

        // Projecao enxuta: sem o base64 das imagens (ver ONGRepository.listagemLeve).
        List<Object[]> lista =
                (nome != null && !nome.isEmpty())
                        ? repository.listagemLevePorNome(nome)
                        : repository.listagemLeve();

        // BLOQUEIO: na busca feita por um DOADOR autenticado, as ONGs que o
        // bloquearam nao aparecem (anonimo/ONG = conjunto vazio, nao filtra).
        Set<Long> ongsBloqueadoras =
                bloqueioService.ongIdsQueBloquearamDoadorAtual();

        // PERFORMANCE: privacidade de TODAS as ONGs numa consulta so (antes cada
        // ONG disparava 2 consultas dentro do toDTO = ~40 idas ao banco para 20
        // ONGs; com o banco longe do servidor isso custava ~6,9s). Ver
        // PreferenciaRepository.privacidadePorOng().
        Map<Long, boolean[]> privacidades = privacidadesPorOng();

        // PERFORMANCE: a listagem NAO leva imagem nenhuma (nem capa, nem logo).
        // Ela devolve TODAS as ONGs de uma vez (hoje 2.000) — com a demonstracao
        // ilustrada, uma capa por ONG jogaria a resposta de 2,4 MB para ~80 MB.
        // Quem quiser a imagem de um card busca por URL, uma a uma e so quando o
        // card aparece na tela: GET /publico/ongs/{id}/logo e .../capa (o
        // navegador ainda guarda em cache). O perfil detalhado continua trazendo
        // as imagens embutidas em base64, como antes.
        // (A consulta ja filtra data_exclusao; aqui so resta o filtro de bloqueio.)
        return lista.stream()
                .map(l -> toDTO(l, privacidades))
                .filter(dto -> !ongsBloqueadoras.contains(dto.getId()))
                .collect(Collectors.toList());
    }

    // Defaults seguros, iguais aos de privacidadeDaOng: email oculto, telefone visivel.
    private static final boolean[] PRIVACIDADE_PADRAO = { false, true };

    /** Mapa ongId -> {mostrarEmail, mostrarTelefone} carregado em UMA consulta. */
    private Map<Long, boolean[]> privacidadesPorOng() {
        Map<Long, boolean[]> mapa = new HashMap<>();
        for (Object[] l : preferenciaRepository.privacidadePorOng()) {
            if (l == null || l[0] == null) continue;
            Long ongId = ((Number) l[0]).longValue();
            Boolean email = paraBoolean(l[1]);
            Boolean telefone = paraBoolean(l[2]);
            // Sem linha de preferencia (NULL) => mesmos defaults de antes.
            mapa.put(ongId, new boolean[] {
                    email != null ? email : false,
                    telefone != null ? telefone : true });
        }
        return mapa;
    }

    // O driver pode devolver BIT(1) como Boolean, Number ou byte[] — normaliza.
    private static Boolean paraBoolean(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        if (v instanceof byte[] arr) return arr.length > 0 && arr[0] != 0;
        return null;
    }

    // =========================
    // BUSCAR POR ID (perfil completo, com capa/endereco/fotos do local)
    // =========================
    public ResponseEntity<?> obterPorId(Long id) {
        Ong ong = repository.findById(id).orElse(null);
        if (ong == null || ong.getDataExclusao() != null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDTORico(ong));
    }

    // =========================
    // ATUALIZAR
    // =========================
    @Transactional
    public ResponseEntity<?> atualizar(
            Long id,
            OngUpdateDTO dados
    ) {

        // So a propria ONG dona pode editar o seu perfil (senao 403).
        security.exigirOng(id);

        return repository.findById(id)
                .map(ong -> {

                    // Campos OBRIGATORIOS na entidade (@NotBlank): so sobrescrevem
                    // quando vem preenchidos. Motivo real: o GET do perfil NAO
                    // devolve o e-mail (privacidade), entao o painel da ONG o
                    // carregava vazio e reenviava "" ao salvar — a validacao da
                    // entidade estourava no flush e a resposta virava 500. Na
                    // pratica, a ONG nao conseguia salvar NADA no perfil.
                    if (naoVazio(dados.getNome())) {
                        ong.setNome(dados.getNome());
                    }
                    if (naoVazio(dados.getEmail())) {
                        ong.setEmail(dados.getEmail());
                    }
                    // Opcionais: null = nao mexe; vazio = a ONG limpou de fato.
                    if (dados.getTelefone() != null) {
                        ong.setTelefone(dados.getTelefone());
                    }
                    if (dados.getCidade() != null) {
                        ong.setCidade(dados.getCidade());
                    }
                    if (dados.getDescricao() != null) {
                        ong.setDescricao(dados.getDescricao());
                    }

                    // Perfil rico: capa/endereco so sobrescrevem quando enviados
                    // (null = mantem o atual, igual a foto do PerfilService).
                    if (dados.getLogoBase64() != null) {
                        ong.setLogoBase64(dados.getLogoBase64());
                    }
                    if (dados.getCapaBase64() != null) {
                        ong.setCapaBase64(dados.getCapaBase64());
                    }
                    if (dados.getEndereco() != null) {
                        ong.setEndereco(dados.getEndereco());
                    }
                    // Coordenadas do endereco (opcionais): so sobrescrevem quando
                    // enviadas E dentro de faixa geografica valida (evita gravar
                    // lixo). Enviar 0/0 ou fora da faixa = ignora, mantem o atual.
                    if (dados.getLatitude() != null && dados.getLongitude() != null
                            && coordenadaValida(dados.getLatitude(), dados.getLongitude())) {
                        ong.setLatitude(dados.getLatitude());
                        ong.setLongitude(dados.getLongitude());
                    }

                    Ong atualizada = repository.save(ong);

                    // Fotos do local: a lista enviada SUBSTITUI as existentes
                    // (max 5, ja validado no DTO). Null = nao mexe.
                    if (dados.getFotosLocal() != null) {
                        ongFotoRepository.deleteByOngId(id);
                        for (String foto : dados.getFotosLocal()) {
                            if (foto == null || foto.isBlank()) continue;
                            OngFoto of = new OngFoto();
                            of.setOngId(id);
                            of.setFoto(foto);
                            ongFotoRepository.save(of);
                        }
                    }

                    return ResponseEntity.ok(toDTORico(atualizada));

                }).orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // =========================
    // DELETAR
    // =========================
    public ResponseEntity<?> deletar(
            Long id
    ) {

        // So a propria ONG dona pode excluir a sua conta (senao 403).
        security.exigirOng(id);

        // SOFT-DELETE: nao remove fisicamente (evita orfaos/erro de FK e preserva o
        // historico). Marca a ONG e a conta de login vinculada como excluidas; a
        // ONG some das listagens/ranking/perfil publico e a conta deixa de logar.
        return repository.findById(id)
                .map(ong -> {

                    java.time.LocalDateTime agora = java.time.LocalDateTime.now();
                    ong.setDataExclusao(agora);
                    repository.save(ong);

                    usuarioRepository.findByOngId(ong.getId()).ifPresent(conta -> {
                        conta.setDataExclusao(agora);
                        usuarioRepository.save(conta);
                    });

                    return ResponseEntity
                            .noContent()
                            .build();

                }).orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // =========================
    // ERRO PADRÃO
    // =========================
    // =========================
    // VERIFICAR (concede o selo de confianca)
    // =========================
    // Conceder o selo e uma acao ADMINISTRATIVA: o endpoint PUT /ongs/{id}/verificar
    // e restrito a ROLE_ADMIN no SecurityConfig (papel dedicado, nao auto-provisionavel).
    // Por isso NAO ha checagem de dono aqui — o admin nao possui ongId; a autorizacao
    // ja aconteceu na camada de seguranca. Antes exigia a ONG dona, o que permitia a
    // auto-verificacao e esvaziava o sentido do selo.
    public ResponseEntity<?> verificar(Long id) {
        return repository.findById(id)
                .map(ong -> {
                    ong.setVerificada(true);
                    return ResponseEntity.ok(toDTO(repository.save(ong)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Monta o DTO a partir da PROJECAO da listagem (ver ONGRepository.listagemLeve).
     * A ordem dos campos aqui e a mesma do select — mexer num lado exige mexer no
     * outro. Vale a pena: e o que evita ler dezenas de MB de imagem a cada
     * listagem so para descartar.
     */
    private OngResponseDTO toDTO(Object[] l, Map<Long, boolean[]> privacidades) {
        Long id = ((Number) l[0]).longValue();
        boolean[] p = privacidades.getOrDefault(id, PRIVACIDADE_PADRAO);
        OngResponseDTO dto = new OngResponseDTO(
                id,
                (String) l[1],
                p[0] ? (String) l[2] : null,
                p[1] ? (String) l[3] : null,
                (String) l[4],
                (String) l[5],
                (String) l[6],
                (Boolean) l[7],
                (Double) l[8],
                (Integer) l[9]
        );
        dto.setLatitude((Double) l[10]);
        dto.setLongitude((Double) l[11]);
        return dto;
    }

    private OngResponseDTO toDTO(Ong o) {
        // Caminho de UMA ONG (detalhe): busca a privacidade dela na hora.
        return toDTO(o, privacidadeDaOng(o.getId()));
    }

    /**
     * Igual ao {@link #toDTO(Ong)}, mas recebe a privacidade JA carregada — usado
     * pela listagem, que traz a de todas as ONGs numa consulta so (evita N+1).
     */
    private OngResponseDTO toDTO(Ong o, boolean[] p) {
        // PRIVACIDADE REAL: a listagem GET /ongs e o detalhe GET /ongs/{id}
        // respeitam os MESMOS toggles mostrarEmail/mostrarTelefone do perfil
        // publico. Antes esses 2 caminhos devolviam contato de qualquer ONG a
        // qualquer autenticado, ignorando as configuracoes (o perfil publico ja
        // respeitava). Agora o contato so aparece quando a ONG dona o liberou.
        OngResponseDTO dto = new OngResponseDTO(
                o.getId(),
                o.getNome(),
                p[0] ? o.getEmail() : null,
                p[1] ? o.getTelefone() : null,
                o.getCidade(),
                o.getDescricao(),
                o.getCnpj(),
                o.getVerificada(),
                o.getNotaMedia(),
                o.getTotalAvaliacoes()
        );
        // Coordenadas: leves (2 doubles), incluidas em TODOS os caminhos
        // (listagem/detalhe/publico) para o mapa do web apontar o local exato.
        dto.setLatitude(o.getLatitude());
        dto.setLongitude(o.getLongitude());
        return dto;
    }

    // Versao "rica" do DTO (GET /ongs/{id} e resposta do PUT): inclui capa,
    // endereco e fotos do local. A listagem continua com o toDTO enxuto para
    // nao inflar a resposta com megabytes de base64.
    private OngResponseDTO toDTORico(Ong o) {
        OngResponseDTO dto = toDTO(o);
        dto.setLogoBase64(o.getLogoBase64());
        dto.setCapaBase64(o.getCapaBase64());
        dto.setEndereco(o.getEndereco());
        dto.setFotosLocal(fotosLocal(o.getId()));
        return dto;
    }

    // Fotos do local da ONG (base64), em ordem de insercao.
    private List<String> fotosLocal(Long ongId) {
        return ongFotoRepository.findByOngIdOrderByIdAsc(ongId).stream()
                .map(OngFoto::getFoto)
                .collect(Collectors.toList());
    }

    // Coordenada geografica dentro da faixa valida do planeta E nao exatamente
    // 0/0 (a "Null Island" no Atlantico = tipico valor default/lixo). Evita
    // gravar coordenada invalida vinda de um cliente com bug.
    private boolean coordenadaValida(double lat, double lng) {
        if (lat == 0.0 && lng == 0.0) return false;
        return lat >= -90.0 && lat <= 90.0 && lng >= -180.0 && lng <= 180.0;
    }

    // Coalesce de campo opcional: null -> "" (para nao violar colunas NOT NULL).
    private String naoNulo(String valor) {
        return valor != null ? valor : "";
    }

    // Texto realmente preenchido (nao null e nao so espacos).
    private boolean naoVazio(String valor) {
        return valor != null && !valor.isBlank();
    }

    private ResponseEntity<?> erro(
            String mensagem
    ) {

        Map<String, String> erro =
                new HashMap<>();

        erro.put("erro", mensagem);

        return ResponseEntity
                .badRequest()
                .body(erro);
    }
}