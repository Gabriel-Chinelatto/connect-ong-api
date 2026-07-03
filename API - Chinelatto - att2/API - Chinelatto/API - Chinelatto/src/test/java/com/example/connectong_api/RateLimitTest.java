package com.example.connectong_api;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do rate limiting (RateLimitService):
 * - login: 5 falhas consecutivas (email+IP) -> 6a tentativa responde 429;
 *   um sucesso zera o contador;
 * - esqueci-senha e cadastro publico: maximo 5 solicitacoes/15min por IP.
 *
 * O properties compartilhado dos testes desliga os limites (para nao travar a
 * suite, que compartilha o IP 127.0.0.1); AQUI eles voltam aos valores reais
 * via @TestPropertySource (contexto proprio). Cada teste usa um IP distinto
 * (setRemoteAddr) para nao interferir nos demais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.ratelimit.max-falhas=5",
        "app.ratelimit.max-solicitacoes=5",
        "app.ratelimit.janela-minutos=15"
})
class RateLimitTest {

    private static final String MSG_429 = "Muitas tentativas. Tente novamente em alguns minutos.";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private static RequestPostProcessor ip(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    private void criarUsuario(String email, String senha) {
        Usuario u = new Usuario();
        u.setNome("Usuario RateLimit");
        u.setEmail(email);
        u.setTipo("DOADOR");
        u.setSenha(passwordEncoder.encode(senha));
        usuarioRepository.save(u);
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email, String senha, String addr) throws Exception {
        return mockMvc.perform(post("/usuarios/login")
                .with(ip(addr))
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"));
    }

    @Test
    void login_aSextaFalhaConsecutiva_retorna429_mesmoComSenhaCerta() throws Exception {
        criarUsuario("rl.bloqueio@teste.com", "senha-certa-123");

        // 5 falhas -> todas 401 (ainda nao bloqueado)
        for (int i = 0; i < 5; i++) {
            login("rl.bloqueio@teste.com", "senha-errada", "10.9.1.1")
                    .andExpect(status().isUnauthorized());
        }

        // 6a tentativa -> 429, ANTES de conferir a senha
        login("rl.bloqueio@teste.com", "senha-errada", "10.9.1.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value(MSG_429));

        // mesmo a senha CERTA fica bloqueada enquanto durar o bloqueio
        login("rl.bloqueio@teste.com", "senha-certa-123", "10.9.1.1")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_sucessoZeraOContadorDeFalhas() throws Exception {
        criarUsuario("rl.zera@teste.com", "senha-certa-123");

        // 4 falhas (abaixo do limite)
        for (int i = 0; i < 4; i++) {
            login("rl.zera@teste.com", "senha-errada", "10.9.1.2")
                    .andExpect(status().isUnauthorized());
        }

        // sucesso -> zera o contador
        login("rl.zera@teste.com", "senha-certa-123", "10.9.1.2")
                .andExpect(status().isOk());

        // nova falha volta a contar do 1 -> 401 (e nao 429)
        login("rl.zera@teste.com", "senha-errada", "10.9.1.2")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_bloqueioEporChaveEmailIp_naoAfetaOutroIp() throws Exception {
        criarUsuario("rl.chave@teste.com", "senha-certa-123");

        for (int i = 0; i < 5; i++) {
            login("rl.chave@teste.com", "senha-errada", "10.9.1.3")
                    .andExpect(status().isUnauthorized());
        }
        login("rl.chave@teste.com", "senha-errada", "10.9.1.3")
                .andExpect(status().isTooManyRequests());

        // o mesmo email a partir de OUTRO IP nao esta bloqueado (chave email+IP)
        login("rl.chave@teste.com", "senha-certa-123", "10.9.99.3")
                .andExpect(status().isOk());
    }

    @Test
    void esqueciSenha_aSextaSolicitacaoDoMesmoIp_retorna429() throws Exception {
        // 5 solicitacoes -> 200 (o email nem precisa existir: resposta generica)
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/auth/esqueci-senha")
                            .with(ip("10.9.1.4"))
                            .contentType("application/json")
                            .content("{\"email\":\"spam" + i + "@teste.com\"}"))
                    .andExpect(status().isOk());
        }

        // 6a solicitacao do MESMO IP -> 429 (anti-spam de codigo)
        mockMvc.perform(post("/auth/esqueci-senha")
                        .with(ip("10.9.1.4"))
                        .contentType("application/json")
                        .content("{\"email\":\"spam5@teste.com\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value(MSG_429));
    }

    @Test
    void cadastroPublico_aSextaSolicitacaoDoMesmoIp_retorna429() throws Exception {
        // 5 cadastros -> 201
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/usuarios/registro")
                            .with(ip("10.9.1.5"))
                            .contentType("application/json")
                            .content("{\"nome\":\"Doador " + i + "\",\"email\":\"rl.cad"
                                    + i + "@teste.com\",\"senha\":\"123456\"}"))
                    .andExpect(status().isCreated());
        }

        // 6o cadastro do MESMO IP -> 429 (mitiga enumeracao/cadastro em massa)
        mockMvc.perform(post("/usuarios/registro")
                        .with(ip("10.9.1.5"))
                        .contentType("application/json")
                        .content("{\"nome\":\"Doador 6\",\"email\":\"rl.cad6@teste.com\","
                                + "\"senha\":\"123456\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value(MSG_429));
    }
}
