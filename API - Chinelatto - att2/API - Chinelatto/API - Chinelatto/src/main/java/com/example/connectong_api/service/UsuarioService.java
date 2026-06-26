package com.example.connectong_api.service;

import com.example.connectong_api.dto.CadastroUsuarioDTO;
import com.example.connectong_api.dto.LoginRequestDTO;
import com.example.connectong_api.dto.UsuarioResponseDTO;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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

    // =========================
    // CADASTRO
    // =========================
    @Transactional
    public ResponseEntity<?> cadastrar(CadastroUsuarioDTO dados) {

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
        Usuario usuario = new Usuario();
        usuario.setNome(dados.getNome());
        usuario.setEmail(dados.getEmail());
        usuario.setTipo(dados.getTipo());
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
    // LOGIN
    // =========================
    public ResponseEntity<?> login(LoginRequestDTO credenciais) {

        Optional<Usuario> usuarioBanco =
                usuarioRepository.findByEmail(credenciais.getEmail());

        // usuário encontrado E senha confere -> sucesso
        if (usuarioBanco.isPresent()
                && passwordEncoder.matches(
                        credenciais.getSenha(),
                        usuarioBanco.get().getSenha())) {

            Usuario usuarioEncontrado = usuarioBanco.get();

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