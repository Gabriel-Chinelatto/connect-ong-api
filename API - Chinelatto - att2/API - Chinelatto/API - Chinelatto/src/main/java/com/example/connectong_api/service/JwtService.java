package com.example.connectong_api.service;

import com.example.connectong_api.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Geracao e validacao de tokens JWT (access + refresh).
 * A validacao do token agora E exigida nos endpoints protegidos
 * (ver SecurityConfig / JwtAuthFilter).
 *
 * A chave secreta vem OBRIGATORIAMENTE da propriedade "app.jwt.secret" (mapeada
 * da variavel de ambiente APP_JWT_SECRET em producao). NAO ha mais valor default:
 * se a propriedade estiver ausente a aplicacao FALHA no startup, de proposito —
 * assim nunca voltamos a assinar tokens com um segredo publico/versionado. Em
 * desenvolvimento local o valor vem de application-local.properties (gitignore).
 */
@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(
            @Value("${app.jwt.secret}")
            String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 12h: cobre uma sessao longa (ex.: dia de feira) sem exigir refresh
    // automatico no cliente. O refresh token (7 dias) continua disponivel.
    private static final long ACCESS_MS = 1000L * 60 * 60 * 12;       // 12 horas
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
