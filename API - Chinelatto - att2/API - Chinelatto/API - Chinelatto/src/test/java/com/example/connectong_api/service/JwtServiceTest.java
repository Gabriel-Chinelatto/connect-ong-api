package com.example.connectong_api.service;

import com.example.connectong_api.model.Usuario;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de unidade do JwtService (sem Spring/banco): geracao e validacao
 * de tokens. Rodam em qualquer ambiente, inclusive na CI.
 */
class JwtServiceTest {

    private final JwtService jwt = new JwtService();

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setId(42L);
        u.setNome("Maria");
        u.setTipo("DOADOR");
        return u;
    }

    @Test
    void accessTokenContemAsClaims() {
        Claims c = jwt.validar(jwt.gerarAccessToken(usuario()));
        assertEquals("42", c.getSubject());
        assertEquals("Maria", c.get("nome", String.class));
        assertEquals("DOADOR", c.get("tipo", String.class));
        assertEquals("access", c.get("tokenType", String.class));
    }

    @Test
    void refreshTokenTemTipoRefresh() {
        Claims c = jwt.validar(jwt.gerarRefreshToken(usuario()));
        assertEquals("refresh", c.get("tokenType", String.class));
        assertEquals("42", c.getSubject());
    }

    @Test
    void tokenAdulteradoEhRejeitado() {
        String token = jwt.gerarAccessToken(usuario());
        String adulterado = token.substring(0, token.length() - 3) + "abc";
        assertThrows(Exception.class, () -> jwt.validar(adulterado));
    }
}
