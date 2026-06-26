package com.example.connectong_api.service;

import com.example.connectong_api.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Geracao e validacao de tokens JWT (access + refresh).
 * A infraestrutura esta pronta; a exigencia do token nos endpoints
 * fica DESLIGADA por enquanto (para nao quebrar a aplicacao no dev).
 */
@Service
public class JwtService {

    // Chave secreta (>= 256 bits para HS256). Em producao iria para variavel de ambiente.
    private static final String SECRET =
            "connect-ong-chave-secreta-jwt-super-segura-2026-256-bits-minimo!!";

    private final SecretKey key =
            Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private static final long ACCESS_MS = 1000L * 60 * 60;            // 1 hora
    private static final long REFRESH_MS = 1000L * 60 * 60 * 24 * 7;  // 7 dias

    public String gerarAccessToken(Usuario u) {
        return Jwts.builder()
                .subject(String.valueOf(u.getId()))
                .claim("nome", u.getNome())
                .claim("tipo", u.getTipo())
                .claim("ongId", u.getOngId())
                .claim("tokenType", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_MS))
                .signWith(key)
                .compact();
    }

    public String gerarRefreshToken(Usuario u) {
        return Jwts.builder()
                .subject(String.valueOf(u.getId()))
                .claim("tokenType", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_MS))
                .signWith(key)
                .compact();
    }

    /** Valida o token e retorna as claims; lanca excecao se invalido/expirado. */
    public Claims validar(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
