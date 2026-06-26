package com.example.connectong_api.controller;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.service.JwtService;

import io.jsonwebtoken.Claims;

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
