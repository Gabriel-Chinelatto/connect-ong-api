package com.example.connectong_api;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que o interruptor "app.demo.enabled=false" desativa os comportamentos
 * de demonstracao: o seed (POST /demo/seed) e o campo codigoDemo do
 * esqueci-senha (simulacao de e-mail da feira) — usado para desligar tudo em
 * producao sem recompilar. Contexto proprio (property override) para nao
 * afetar os demais testes.
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

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Test
    void esqueciSenha_comDemoDesativado_naoExpoeCodigoDemo() throws Exception {
        Usuario u = new Usuario();
        u.setNome("Usuario Producao");
        u.setEmail("reset.producao@teste.com");
        u.setTipo("DOADOR");
        u.setSenha(passwordEncoder.encode("senha-original"));
        usuarioRepository.save(u);

        // Em "producao" (demo off) a resposta e SO a mensagem generica: o codigo
        // e gerado e persistido, mas NUNCA volta no corpo (iria por e-mail real).
        mockMvc.perform(post("/auth/esqueci-senha")
                        .contentType("application/json")
                        .content("{\"email\":\"reset.producao@teste.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").exists())
                .andExpect(jsonPath("$.codigoDemo").doesNotExist());
    }
}
