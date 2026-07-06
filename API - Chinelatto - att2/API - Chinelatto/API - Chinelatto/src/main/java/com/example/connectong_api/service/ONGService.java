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
        Usuario conta = usuarioRepository.findByOngId(ongId).orElse(null);
        if (conta == null) {
            return;
        }
        Preferencia prefs = preferenciaRepository.findByUsuarioId(conta.getId())
                .orElseGet(() -> Preferencia.padrao(conta.getId()));

        // Campo null no registro (linha antiga, coluna criada depois) recai nos
        // defaults do padrao(): email oculto, telefone visivel.
        boolean exibirEmail = prefs.getMostrarEmail() != null
                ? prefs.getMostrarEmail() : false;
        boolean exibirTelefone = prefs.getMostrarTelefone() != null
                ? prefs.getMostrarTelefone() : true;

        if (!exibirEmail) {
            dto.setEmail(null);
        }
        if (!exibirTelefone) {
            dto.setTelefone(null);
        }
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

        List<Ong> lista;

        if (nome != null &&
                !nome.isEmpty()) {

            lista =
                    repository.findByNomeContainingIgnoreCase(nome);

        } else {

            lista =
                    repository.findAll();
        }

        // BLOQUEIO: na busca feita por um DOADOR autenticado, as ONGs que o
        // bloquearam nao aparecem (anonimo/ONG = conjunto vazio, nao filtra).
        Set<Long> ongsBloqueadoras =
                bloqueioService.ongIdsQueBloquearamDoadorAtual();

        return lista.stream()
                .filter(o -> o.getDataExclusao() == null) // esconde ONGs excluidas
                .filter(o -> !ongsBloqueadoras.contains(o.getId()))
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================
    // CRIAR
    // =========================
    public ResponseEntity<?> criar(
            Ong ong
    ) {

        // valida nome
        if (ong.getNome() == null ||
                ong.getNome().isEmpty()) {

            return erro(
                    "Nome da ONG é obrigatório"
            );
        }

        // valida email
        if (ong.getEmail() == null ||
                ong.getEmail().isEmpty()) {

            return erro(
                    "Email é obrigatório"
            );
        }

        // Anti mass-assignment: campos de confianca/agregados NUNCA vem do cliente.
        // (o id ja e ignorado por nao ter setter; aqui zeramos o resto por seguranca)
        ong.setVerificada(false);
        ong.setNotaMedia(0.0);
        ong.setTotalAvaliacoes(0);

        Ong nova =
                repository.save(ong);

        return ResponseEntity.ok(toDTO(nova));
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

                    ong.setNome(dados.getNome());
                    ong.setEmail(dados.getEmail());
                    ong.setTelefone(dados.getTelefone());
                    ong.setCidade(dados.getCidade());
                    ong.setDescricao(dados.getDescricao());

                    // Perfil rico: capa/endereco so sobrescrevem quando enviados
                    // (null = mantem o atual, igual a foto do PerfilService).
                    if (dados.getCapaBase64() != null) {
                        ong.setCapaBase64(dados.getCapaBase64());
                    }
                    if (dados.getEndereco() != null) {
                        ong.setEndereco(dados.getEndereco());
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

    private OngResponseDTO toDTO(Ong o) {
        return new OngResponseDTO(
                o.getId(),
                o.getNome(),
                o.getEmail(),
                o.getTelefone(),
                o.getCidade(),
                o.getDescricao(),
                o.getCnpj(),
                o.getVerificada(),
                o.getNotaMedia(),
                o.getTotalAvaliacoes()
        );
    }

    // Versao "rica" do DTO (GET /ongs/{id} e resposta do PUT): inclui capa,
    // endereco e fotos do local. A listagem continua com o toDTO enxuto para
    // nao inflar a resposta com megabytes de base64.
    private OngResponseDTO toDTORico(Ong o) {
        OngResponseDTO dto = toDTO(o);
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

    // Coalesce de campo opcional: null -> "" (para nao violar colunas NOT NULL).
    private String naoNulo(String valor) {
        return valor != null ? valor : "";
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