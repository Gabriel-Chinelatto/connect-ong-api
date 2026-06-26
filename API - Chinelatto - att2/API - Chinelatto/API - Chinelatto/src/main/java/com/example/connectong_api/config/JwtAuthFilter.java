package com.example.connectong_api.config;

import com.example.connectong_api.security.UsuarioAutenticado;
import com.example.connectong_api.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que, a cada requisicao, le o header "Authorization: Bearer <token>",
 * valida o JWT e popula o SecurityContext com o usuario autenticado.
 *
 * - Token ausente ou invalido: a requisicao segue SEM autenticacao. Se a rota
 *   for protegida (ver SecurityConfig), o proprio Spring Security devolve 401/403.
 * - Refresh tokens NAO autenticam aqui (so servem em /auth/refresh).
 *
 * O principal e o id do usuario (subject do token); a authority e ROLE_<tipo>
 * (ROLE_DOADOR ou ROLE_ONG), permitindo regras por papel no futuro.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.validar(token);

                // refresh token nao serve para autenticar requisicoes comuns
                if (!"refresh".equals(claims.get("tokenType", String.class))) {
                    String tipo = claims.get("tipo", String.class);
                    List<SimpleGrantedAuthority> authorities = tipo != null
                            ? List.of(new SimpleGrantedAuthority("ROLE_" + tipo))
                            : List.of();

                    // Principal rico: id + tipo + ongId, para checagens de ownership.
                    Long id = Long.valueOf(claims.getSubject());
                    Long ongId = null;
                    Object ongClaim = claims.get("ongId");
                    if (ongClaim instanceof Number numero) {
                        ongId = numero.longValue();
                    }
                    UsuarioAutenticado principal =
                            new UsuarioAutenticado(id, tipo, ongId);

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    principal, null, authorities);

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignorado) {
                // token invalido/expirado: segue sem autenticacao
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
