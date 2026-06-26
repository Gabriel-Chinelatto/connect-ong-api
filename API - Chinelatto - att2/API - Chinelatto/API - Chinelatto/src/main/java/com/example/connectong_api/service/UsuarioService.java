package com.example.connectong_api.service;

import com.example.connectong_api.dto.UsuarioResponseDTO;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
    public ResponseEntity<?> cadastrar(Usuario usuario) {

        // valida campos obrigatórios
        if (usuario.getNome() == null || usuario.getNome().isEmpty() ||
                usuario.getEmail() == null || usuario.getEmail().isEmpty() ||
                usuario.getSenha() == null || usuario.getSenha().isEmpty() ||
                usuario.getTipo() == null || usuario.getTipo().isEmpty()) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Preencha todos os campos");

            return ResponseEntity
                    .badRequest()
                    .body(erro);
        }

        // valida email duplicado
        if (usuarioRepository.findByEmail(usuario.getEmail()).isPresent()) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Email já cadastrado");

            return ResponseEntity
                    .badRequest()
                    .body(erro);
        }

        // criptografa senha
        usuario.setSenha(
                passwordEncoder.encode(usuario.getSenha())
        );

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
    public ResponseEntity<?> login(Usuario usuario) {

        // valida email e senha
        if (usuario.getEmail() == null || usuario.getEmail().isEmpty() ||
                usuario.getSenha() == null || usuario.getSenha().isEmpty()) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Informe email e senha");

            return ResponseEntity
                    .badRequest()
                    .body(erro);
        }

        Optional<Usuario> usuarioBanco =
                usuarioRepository.findByEmail(usuario.getEmail());

        // usuário encontrado
        if (usuarioBanco.isPresent()) {

            Usuario usuarioEncontrado =
                    usuarioBanco.get();

            // valida senha criptografada
            if (passwordEncoder.matches(
                    usuario.getSenha(),
                    usuarioEncontrado.getSenha()
            )) {

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

            auditService.registrar("LOGIN_FALHA", usuarioEncontrado.getId(),
                    "Senha incorreta para: " + usuarioEncontrado.getEmail());

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Senha incorreta");

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(erro);
        }

        auditService.registrar("LOGIN_FALHA", null,
                "Tentativa de login com email nao cadastrado: " + usuario.getEmail());

        Map<String, String> erro = new HashMap<>();
        erro.put("erro", "Usuário não encontrado");

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(erro);
    }
}