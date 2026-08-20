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
    // desenvolvimento local (apps Flutter web/desktop e Swagger) E a WEB EM
    // PRODUCAO (Netlify). O proxy do Netlify repassa o header Origin do
    // navegador para ca; sem o dominio nesta lista o Spring responde
    // "Invalid CORS request" (403) no login. Para outro dominio, defina a
    // variavel de ambiente APP_CORS_ALLOWED_ORIGINS.
    // O app do doador e o painel da ONG tambem sao publicados como site
    // estatico no GitHub Pages (github.io), para abrirem em qualquer maquina
    // sem instalar Flutter. Os dois ficam sob o MESMO dominio
    // (gabriel-chinelatto.github.io), que precisa constar aqui.
    @Value("${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,https://connectong.netlify.app,https://gabriel-chinelatto.github.io}")
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
                            // Assistente de doacao: PUBLICO (funciona sem login; se
                            // houver token, o filtro JWT ainda o le e o service usa a
                            // cidade do perfil). Protegido por rate limiting proprio.
                            // Inclui /assistente e /assistente/sugestoes (proativas).
                            .requestMatchers(HttpMethod.POST, "/assistente", "/assistente/sugestoes").permitAll()
                            // Assistente "Sobre o Desenvolvimento": explica COMO o
                            // projeto foi feito (grounding num doc curado). PUBLICO
                            // (aparece na tela Sobre, com/sem login). Rate limit proprio.
                            .requestMatchers(HttpMethod.POST, "/assistente-dev").permitAll()
                            // Estimativa de frete de doacoes: PUBLICO, rate limit proprio.
                            .requestMatchers(HttpMethod.POST, "/frete/estimar").permitAll()
                            // Apoios de IA (com fallback por regras): redacao da necessidade
                            // (painel da ONG) e resumo de impacto (doador). PUBLICOS, rate
                            // limit proprio cada.
                            .requestMatchers(HttpMethod.POST, "/ia/redacao", "/ia/resumo-impacto", "/ia/sobre-ong").permitAll()
                            // Diagnostico da IA (GET /ia/status): diz se a chave esta
                            // configurada NAQUELE ambiente e qual foi o ultimo erro da
                            // Groq. NAO expoe a chave — so um booleano e o nome dos
                            // modelos. Publico de proposito: e o jeito de conferir a IA
                            // no Render, do celular, minutos antes de apresentar.
                            .requestMatchers(HttpMethod.GET, "/ia/status").permitAll()
                            // Perfil publico do DOADOR e suas avaliacoes: leitura
                            // livre (reputacao publica, como o perfil da ONG). O
                            // service nunca expoe email/telefone/valores.
                            .requestMatchers(HttpMethod.GET, "/usuarios/*/perfil-publico").permitAll()
                            .requestMatchers(HttpMethod.GET, "/avaliacoes-doador").permitAll()
                            // Perfil publico da ONG: leitura livre — e o destino do
                            // LINK COMPARTILHAVEL (/#/ong/{id}); sem isso, quem abre
                            // o link deslogado recebia 401 e a tela quebrava.
                            .requestMatchers(HttpMethod.GET, "/ongs/*/perfil-publico").permitAll()
                            // Recursos ADMINISTRATIVOS/MODERACAO: exigem ROLE_ADMIN, um
                            // papel dedicado e NAO auto-provisionavel (ver AdminBootstrap).
                            // Antes eram ROLE_ONG, mas ONG e auto-registravel (qualquer um
                            // faz POST /ongs/registro), entao qualquer pessoa ganhava acesso
                            // de moderacao/auditoria. Agora isso e privilegio so do admin.
                            // Auditoria (contem IP/emails): so o admin le.
                            .requestMatchers("/audit-logs/**").hasRole("ADMIN")
                            // Denuncias: registrar (POST) fica liberado a qualquer logado
                            // (um doador precisa poder reportar); ja listar e resolver
                            // (moderacao) exigem ROLE_ADMIN.
                            .requestMatchers(HttpMethod.GET, "/denuncias/**").hasRole("ADMIN")
                            .requestMatchers(HttpMethod.PUT, "/denuncias/**").hasRole("ADMIN")
                            // Conceder o selo de verificacao de uma ONG e ato administrativo
                            // (antes a propria ONG se auto-verificava, esvaziando o selo).
                            .requestMatchers(HttpMethod.PUT, "/ongs/*/verificar").hasRole("ADMIN")
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
