package com.example.connectong_api.service;

import com.example.connectong_api.dto.CadastroUsuarioDTO;
import com.example.connectong_api.dto.LoginRequestDTO;
import com.example.connectong_api.dto.RegistroDoadorDTO;
import com.example.connectong_api.dto.UsuarioResponseDTO;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.PreferenciaRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Responsavel pelo cadastro e login dos usuarios. A senha e sempre armazenada em
 * hash BCrypt (nunca em texto puro) e id/ongId nunca vem do cliente, evitando
 * mass assignment. O login emite tokens JWT (access + refresh); por seguranca
 * nao distingue email inexistente de senha errada (401 generico) e tudo e auditado.
 */
@Service
public class UsuarioService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private PreferenciaRepository preferenciaRepository;

    @Autowired
    private DoisFatoresService doisFatoresService;

    // Simulacao de envio (feira): quando ligado, a resposta que exige 2FA traz
    // o codigo em codigoDemo (mesmo precedente do esqueci-senha e do PIX).
    @Value("${app.demo.enabled:false}")
    private boolean demoEnabled;

    // =========================
    // CADASTRO
    // =========================
    @Transactional
    public ResponseEntity<?> cadastrar(CadastroUsuarioDTO dados) {

        // Rate limiting por IP no cadastro publico. DECISAO: a mensagem "Email
        // já cadastrado" permite enumerar contas, mas foi MANTIDA pela UX da
        // feira (o visitante precisa entender o erro na hora); a mitigacao da
        // enumeracao e este limite de ~5 cadastros/15min por IP, que inviabiliza
        // varredura em massa (ver comentario no RateLimitService).
        if (rateLimitService.excedeuSolicitacoes("cadastro")) {
            return RateLimitService.resposta429();
        }

        // valida email duplicado
        if (usuarioRepository.findByEmail(dados.getEmail()).isPresent()) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Email já cadastrado");

            return ResponseEntity
                    .badRequest()
                    .body(erro);
        }

        // Monta a entidade SO com os campos permitidos. id e ongId nunca vêm do
        // cliente (evita mass assignment / escalonamento de privilegio).
        // SEGURANCA: o tipo e SEMPRE forcado para DOADOR nesta rota publica. Antes
        // o tipo vinha do cliente e, como /usuarios esta na whitelist publica,
        // qualquer um podia POST /usuarios {"tipo":"ONG"} e receber um JWT com
        // ROLE_ONG (escalonamento de privilegio). A unica forma legitima de criar
        // uma conta ONG e /ongs/registro (que tambem cria o perfil e o vinculo).
        Usuario usuario = new Usuario();
        usuario.setNome(dados.getNome());
        usuario.setEmail(dados.getEmail());
        usuario.setTipo("DOADOR");
        usuario.setSenha(passwordEncoder.encode(dados.getSenha()));

        Usuario novo =
                usuarioRepository.save(usuario);

        UsuarioResponseDTO resposta =
                new UsuarioResponseDTO(
                        novo.getId(),
                        novo.getNome(),
                        novo.getEmail(),
                        novo.getTipo(),
                        novo.getOngId()
                );

        resposta.setAccessToken(jwtService.gerarAccessToken(novo));
        resposta.setRefreshToken(jwtService.gerarRefreshToken(novo));

        auditService.registrar("CADASTRO_USUARIO", novo.getId(),
                "Novo usuario cadastrado (" + novo.getTipo() + "): " + novo.getEmail());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(resposta);
    }

    // =========================
    // REGISTRO PUBLICO DE DOADOR (app mobile)
    // =========================
    @Transactional
    public ResponseEntity<?> registrarDoador(RegistroDoadorDTO dados) {

        // Mesmo limite por IP do cadastro (mitigacao de enumeracao de email:
        // mantivemos a mensagem "Email já cadastrado" pela UX da feira e
        // limitamos as tentativas — ver comentario em cadastrar()).
        if (rateLimitService.excedeuSolicitacoes("cadastro")) {
            return RateLimitService.resposta429();
        }

        // email duplicado -> 409 Conflict (o mobile exibe body['erro'])
        if (usuarioRepository.findByEmail(dados.getEmail()).isPresent()) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Email já cadastrado");

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(erro);
        }

        // Monta a entidade SO com os campos permitidos; o tipo e SEMPRE DOADOR
        // (nunca vem do cliente) e a senha e gravada como hash BCrypt, o mesmo
        // formato que o login valida com passwordEncoder.matches().
        Usuario usuario = new Usuario();
        usuario.setNome(dados.getNome());
        usuario.setEmail(dados.getEmail());
        usuario.setTipo("DOADOR");
        usuario.setSenha(passwordEncoder.encode(dados.getSenha()));
        usuario.setTelefone(dados.getTelefone());
        usuario.setCidade(dados.getCidade());
        usuario.setEstado(dados.getEstado());

        Usuario novo =
                usuarioRepository.save(usuario);

        auditService.registrar("CADASTRO_DOADOR", novo.getId(),
                "Novo doador registrado pelo app: " + novo.getEmail());

        // Corpo de sucesso EXATAMENTE como o mobile espera (sem senha, sem tokens)
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("id", novo.getId());
        resposta.put("nome", novo.getNome());
        resposta.put("email", novo.getEmail());
        resposta.put("tipo", novo.getTipo());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(resposta);
    }

    // =========================
    // LOGIN
    // =========================
    public ResponseEntity<?> login(LoginRequestDTO credenciais) {

        // Anti-forca-bruta: apos 5 falhas consecutivas para a mesma chave
        // email+IP, o login fica bloqueado por 15 minutos -> 429 (sem nem
        // conferir a senha). Um login com sucesso zera o contador.
        String chaveLogin = credenciais.getEmail() + "|" + rateLimitService.ipDaRequisicao();
        if (rateLimitService.bloqueadoPorFalhas("login", chaveLogin)) {
            return RateLimitService.resposta429();
        }

        Optional<Usuario> usuarioBanco =
                usuarioRepository.findByEmail(credenciais.getEmail());

        // usuário encontrado, ATIVO (nao excluido) E senha confere -> sucesso.
        // Conta com soft-delete (dataExclusao != null) cai no 401 generico abaixo
        // (mesma resposta de credencial invalida, sem revelar que a conta existiu).
        if (usuarioBanco.isPresent()
                && usuarioBanco.get().getDataExclusao() == null
                && passwordEncoder.matches(
                        credenciais.getSenha(),
                        usuarioBanco.get().getSenha())) {

            Usuario usuarioEncontrado = usuarioBanco.get();

            // A senha ja esta correta: zera o contador de falhas da chave email+IP
            // (vale tanto para o login normal quanto para o desafio 2FA abaixo).
            rateLimitService.limparFalhas("login", chaveLogin);

            // 2FA: se a conta ligou a verificacao em duas etapas (doisFatores=1),
            // NAO emite tokens aqui. Gera um codigo de 6 digitos e responde
            // {requer2fa:true, email, [codigoDemo]}; o cliente confirma em
            // POST /auth/login-2fa. doisFatores=0/null => segue o login normal.
            Preferencia pref = preferenciaRepository
                    .findByUsuarioId(usuarioEncontrado.getId()).orElse(null);
            if (pref != null && Integer.valueOf(1).equals(pref.getDoisFatores())) {
                String codigo = doisFatoresService.gerarCodigo(usuarioEncontrado);

                Map<String, Object> desafio = new LinkedHashMap<>();
                desafio.put("requer2fa", true);
                desafio.put("email", usuarioEncontrado.getEmail());
                if (demoEnabled) {
                    desafio.put("codigoDemo", codigo);
                }
                return ResponseEntity.ok(desafio);
            }

            UsuarioResponseDTO resposta =
                    new UsuarioResponseDTO(
                            usuarioEncontrado.getId(),
                            usuarioEncontrado.getNome(),
                            usuarioEncontrado.getEmail(),
                            usuarioEncontrado.getTipo(),
                            usuarioEncontrado.getOngId()
                    );

            resposta.setAccessToken(jwtService.gerarAccessToken(usuarioEncontrado));
            resposta.setRefreshToken(jwtService.gerarRefreshToken(usuarioEncontrado));

            auditService.registrar("LOGIN_SUCESSO", usuarioEncontrado.getId(),
                    "Login bem-sucedido: " + usuarioEncontrado.getEmail());

            return ResponseEntity.ok(resposta);
        }

        // Falha: NAO distinguir "email inexistente" de "senha errada" (evita
        // enumeracao de usuarios). Sempre 401 com mensagem generica.
        rateLimitService.registrarFalha("login", chaveLogin);
        Long idAuditado = usuarioBanco.map(Usuario::getId).orElse(null);
        auditService.registrar("LOGIN_FALHA", idAuditado,
                "Tentativa de login invalida para: " + credenciais.getEmail());

        Map<String, String> erro = new HashMap<>();
        erro.put("erro", "Credenciais inválidas");

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(erro);
    }
}