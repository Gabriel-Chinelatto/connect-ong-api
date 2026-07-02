package com.example.connectong_api.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuracao de seguranca da API.
 *
 * Antes: o token JWT era gerado mas NUNCA validado -> todos os endpoints abertos.
 * Agora: cadeia stateless que valida o Bearer token (via JwtAuthFilter) e exige
 * autenticacao em tudo, exceto a whitelist publica (login, cadastro, /publico,
 * Swagger). CORS deixa de ser "*" e passa a aceitar so as origens configuradas.
 *
 * Valvula de escape para demonstracao/feira: a propriedade "app.security.enforce"
 * (default = true) pode ser posta como false para liberar tudo temporariamente,
 * sem recompilar. Em producao deve permanecer true.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.security.enforce:true}")
    private boolean enforce;

    // Origens permitidas para CORS (separadas por virgula). Default cobre o
    // desenvolvimento local (apps Flutter web/desktop e Swagger).
    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                // Sem token em rota protegida -> 401 (e nao o 403 padrao do Spring)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                                        "Autenticacao necessaria")))
                .authorizeHttpRequests(auth -> {
                    if (!enforce) {
                        // Modo demonstracao: libera tudo (NAO usar em producao).
                        auth.anyRequest().permitAll();
                        return;
                    }
                    auth
                            // Endpoints publicos (nao exigem token)
                            .requestMatchers(
                                    "/auth/**",
                                    "/publico/**",
                                    "/categorias",
                                    "/v3/api-docs/**",
                                    "/swagger-ui/**",
                                    "/swagger-ui.html",
                                    "/error"
                            ).permitAll()
                            .requestMatchers(HttpMethod.POST, "/usuarios", "/usuarios/login", "/usuarios/registro").permitAll()
                            .requestMatchers(HttpMethod.POST, "/ongs/registro").permitAll()
                            // Todo o resto exige autenticacao via JWT
                            .anyRequest().authenticated();
                })
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
