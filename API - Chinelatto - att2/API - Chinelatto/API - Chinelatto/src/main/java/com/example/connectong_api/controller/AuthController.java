package com.example.connectong_api.controller;

import com.example.connectong_api.dto.EsqueciSenhaDTO;
import com.example.connectong_api.dto.Login2faDTO;
import com.example.connectong_api.dto.RedefinirSenhaDTO;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.service.DoisFatoresService;
import com.example.connectong_api.service.JwtService;
import com.example.connectong_api.service.SenhaResetService;

import io.jsonwebtoken.Claims;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoints de autenticacao JWT.
 *
 * IMPORTANTE: a exigencia de token nos demais endpoints permanece DESLIGADA
 * durante o desenvolvimento (para nao quebrar a estabilidade). Aqui a
 * infraestrutura JWT fica demonstravel: renovar o access token e validar
 * a sessao atual a partir do Bearer token.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SenhaResetService senhaResetService;

    @Autowired
    private DoisFatoresService doisFatoresService;

    // =========================
    // ESQUECI A SENHA (publico — /auth/** esta na whitelist do SecurityConfig)
    // Contrato fixo com os frontends: body {"email"} -> SEMPRE 200 generico
    // (anti-enumeracao). Com app.demo.enabled=true a resposta traz codigoDemo
    // (simulacao de e-mail para a feira; ver javadoc do SenhaResetService).
    // =========================
    @PostMapping("/esqueci-senha")
    public ResponseEntity<?> esqueciSenha(@Valid @RequestBody EsqueciSenhaDTO dto) {
        return senhaResetService.solicitar(dto);
    }

    // =========================
    // REDEFINIR SENHA (publico — /auth/** esta na whitelist do SecurityConfig)
    // Contrato fixo: body {"email","codigo","novaSenha"} -> 200 no sucesso;
    // qualquer falha de codigo -> 400 "Código inválido ou expirado." (generico).
    // =========================
    @PostMapping("/redefinir-senha")
    public ResponseEntity<?> redefinirSenha(@Valid @RequestBody RedefinirSenhaDTO dto) {
        return senhaResetService.redefinir(dto);
    }

    // =========================
    // LOGIN 2FA (publico — /auth/** esta na whitelist do SecurityConfig)
    // Segundo passo do login quando a conta tem doisFatores=1. Contrato: body
    // {"email","codigo"} -> 200 com os MESMOS campos do login normal
    // (accessToken/refreshToken/id/nome/tipo/ongId); codigo invalido/expirado
    // -> 400 "Código inválido ou expirado." (generico).
    // =========================
    @PostMapping("/login-2fa")
    public ResponseEntity<?> login2fa(@Valid @RequestBody Login2faDTO dto) {
        return doisFatoresService.verificarLogin(dto.getEmail(), dto.getCodigo());
    }

    // =========================
    // REFRESH (refresh token valido -> novo access token)
    // =========================
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");

        if (refreshToken == null || refreshToken.isBlank()) {
            return erro(HttpStatus.BAD_REQUEST, "Informe o refreshToken");
        }

        try {
            Claims claims = jwtService.validar(refreshToken);

            if (!"refresh".equals(claims.get("tokenType", String.class))) {
                return erro(HttpStatus.UNAUTHORIZED, "Token nao e um refresh token");
            }

            Long usuarioId = Long.valueOf(claims.getSubject());
            Optional<Usuario> usuario = usuarioRepository.findById(usuarioId);

            if (usuario.isEmpty()) {
                return erro(HttpStatus.UNAUTHORIZED, "Usuario do token nao existe mais");
            }

            // Conta excluida (soft-delete) nao pode renovar o access token: sem
            // isso, uma conta excluida seguiria emitindo tokens por 7 dias via
            // refresh. Login/2FA/reset ja barram soft-delete; aqui fechamos o refresh.
            if (usuario.get().getDataExclusao() != null) {
                return erro(HttpStatus.UNAUTHORIZED, "Conta desativada");
            }

            Map<String, String> resposta = new HashMap<>();
            resposta.put("accessToken", jwtService.gerarAccessToken(usuario.get()));
            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            return erro(HttpStatus.UNAUTHORIZED, "Refresh token invalido ou expirado");
        }
    }

    // =========================
    // ME (valida o Bearer token e retorna os dados da sessao)
    // =========================
    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestHeader(value = "Authorization", required = false) String authorization) {

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return erro(HttpStatus.UNAUTHORIZED, "Token ausente (use o header Authorization: Bearer <token>)");
        }

        String token = authorization.substring(7);

        try {
            Claims claims = jwtService.validar(token);

            Map<String, Object> resposta = new HashMap<>();
            resposta.put("id", Long.valueOf(claims.getSubject()));
            resposta.put("nome", claims.get("nome", String.class));
            resposta.put("tipo", claims.get("tipo", String.class));
            resposta.put("ongId", claims.get("ongId"));
            resposta.put("expiraEm", claims.getExpiration());
            return ResponseEntity.ok(resposta);

        } catch (Exception e) {
            return erro(HttpStatus.UNAUTHORIZED, "Token invalido ou expirado");
        }
    }

    private ResponseEntity<?> erro(HttpStatus status, String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.status(status).body(erro);
    }
}
