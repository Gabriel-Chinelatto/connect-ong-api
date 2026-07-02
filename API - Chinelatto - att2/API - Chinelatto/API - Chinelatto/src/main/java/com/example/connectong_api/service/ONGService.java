package com.example.connectong_api.service;

import com.example.connectong_api.dto.OngRegistroDTO;
import com.example.connectong_api.dto.OngResponseDTO;
import com.example.connectong_api.dto.PerfilPublicoOngDTO;
import com.example.connectong_api.dto.PrestacaoResponseDTO;
import com.example.connectong_api.dto.UsuarioResponseDTO;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.PrestacaoRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.security.SecurityUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private PrestacaoRepository prestacaoRepository;

    @Autowired
    private TransparenciaService transparenciaService;

    @Autowired
    private SecurityUtils security;

    // =========================
    // PERFIL PUBLICO (agrega tudo que o doador ve na pagina da ONG)
    // =========================
    public ResponseEntity<?> perfilPublico(Long id) {
        Ong ong = repository.findById(id).orElse(null);
        if (ong == null) {
            return ResponseEntity.notFound().build();
        }

        List<PrestacaoResponseDTO> prestacoes = prestacaoRepository
                .findByInteresseNecessidadeOngIdOrderByDataCriacaoDesc(id)
                .stream()
                .map(p -> new PrestacaoResponseDTO(
                        p.getId(),
                        p.getInteresse() != null ? p.getInteresse().getId() : null,
                        p.getTitulo(),
                        p.getDescricao(),
                        p.getFotoUrl(),
                        p.getDataCriacao()))
                .collect(Collectors.toList());

        var transp = transparenciaService.calcular(ong);

        PerfilPublicoOngDTO dto = new PerfilPublicoOngDTO(
                ong,
                necessidadeService.listar(id, null, null),
                campanhaService.listar(id, false, null),
                avaliacaoService.listar(id),
                prestacoes,
                transp.getScore(),
                transp.getNivel());

        return ResponseEntity.ok(dto);
    }

    // =========================
    // CADASTRO (cria o perfil da ONG + a conta de login, ja vinculados)
    // =========================
    public ResponseEntity<?> registrar(OngRegistroDTO dto) {

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

        // 1) cria o perfil da ONG
        Ong ong = new Ong(
                dto.getNome(),
                dto.getEmail(),
                dto.getTelefone(),
                dto.getCidade(),
                dto.getDescricao()
        );
        ong.setCnpj(dto.getCnpj());
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

        return lista.stream()
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
    // ATUALIZAR
    // =========================
    public ResponseEntity<?> atualizar(
            Long id,
            Ong ongAtualizada
    ) {

        // So a propria ONG dona pode editar o seu perfil (senao 403).
        security.exigirOng(id);

        return repository.findById(id)
                .map(ong -> {

                    ong.setNome(
                            ongAtualizada.getNome()
                    );

                    ong.setEmail(
                            ongAtualizada.getEmail()
                    );

                    ong.setTelefone(
                            ongAtualizada.getTelefone()
                    );

                    ong.setCidade(
                            ongAtualizada.getCidade()
                    );

                    ong.setDescricao(
                            ongAtualizada.getDescricao()
                    );

                    Ong atualizada =
                            repository.save(ong);

                    return ResponseEntity.ok(toDTO(atualizada));

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

        return repository.findById(id)
                .map(ong -> {

                    repository.delete(ong);

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
    // DECISAO DE SEGURANCA: idealmente conceder o selo e uma acao ADMINISTRATIVA.
    // Como o sistema ainda NAO tem um papel de admin (so DOADOR e ONG), fechamos
    // aqui a IDOR de origem (qualquer logado verificava qualquer ONG) exigindo a
    // propriedade: so a ONG dona alcanca este endpoint. Quando existir um papel
    // de admin, mover esta concessao para ele (restringindo no SecurityConfig).
    public ResponseEntity<?> verificar(Long id) {
        security.exigirOng(id);
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