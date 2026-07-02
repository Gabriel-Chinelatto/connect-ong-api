package com.example.connectong_api;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que o interruptor "app.demo.enabled=false" desativa o seed de demonstracao
 * (POST /demo/seed) — usado para desligar em producao o seed com senha fixa demo123,
 * sem recompilar. Contexto proprio (property override) para nao afetar os demais testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.demo.enabled=false")
class DemoToggleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String tokenDoador() {
        Usuario u = new Usuario();
        u.setId(777L);
        u.setNome("Doador Teste");
        u.setTipo("DOADOR");
        return jwtService.gerarAccessToken(u);
    }

    @Test
    void seed_comDemoDesativado_retorna403() throws Exception {
        mockMvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isForbidden());
    }
}
