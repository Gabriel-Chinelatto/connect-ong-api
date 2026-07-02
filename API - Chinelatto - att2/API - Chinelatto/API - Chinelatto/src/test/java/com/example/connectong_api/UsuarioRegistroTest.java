package com.example.connectong_api;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do cadastro publico de doador (POST /usuarios/registro), consumido
 * pelo app mobile. A rota e publica (whitelist do SecurityConfig): todas as
 * chamadas aqui sao feitas SEM token — se a rota nao estivesse liberada,
 * viriam 401 em vez dos status esperados.
 *
 * Roda contra o H2 em memoria (perfil de teste).
 */
@SpringBootTest
@AutoConfigureMockMvc
class UsuarioRegistroTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private static String json(String nome, String email, String senha) {
        return "{\"nome\":\"" + nome + "\",\"email\":\"" + email
                + "\",\"senha\":\"" + senha + "\",\"telefone\":\"(45) 99999-0000\""
                + ",\"cidade\":\"Toledo\",\"estado\":\"PR\"}";
    }

    @Test
    void registroComSucesso_retorna201_semSenhaNoCorpo() throws Exception {
        // Sem token: prova tambem que a rota e publica
        mockMvc.perform(post("/usuarios/registro")
                        .contentType("application/json")
                        .content(json("Maria Doadora", "maria.registro@teste.com", "123456")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nome").value("Maria Doadora"))
                .andExpect(jsonPath("$.email").value("maria.registro@teste.com"))
                .andExpect(jsonPath("$.tipo").value("DOADOR"))
                // a senha (nem o hash) NUNCA pode voltar no corpo
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("123456"))));

        // No banco: tipo DOADOR e senha em hash BCrypt (mesmo formato que o login valida)
        Usuario salvo = usuarioRepository.findByEmail("maria.registro@teste.com").orElseThrow();
        assertEquals("DOADOR", salvo.getTipo());
        assertNotEquals("123456", salvo.getSenha());
        assertTrue(salvo.getSenha().startsWith("$2"), "senha deve ser hash BCrypt");
        assertTrue(passwordEncoder.matches("123456", salvo.getSenha()),
                "o hash gravado deve validar com o mesmo encoder do login");
        assertEquals("Toledo", salvo.getCidade());
    }

    @Test
    void registroComEmailDuplicado_retorna409() throws Exception {
        mockMvc.perform(post("/usuarios/registro")
                        .contentType("application/json")
                        .content(json("Joao Doador", "joao.duplicado@teste.com", "123456")))
                .andExpect(status().isCreated());

        // Segunda tentativa com o MESMO email -> 409 com mensagem legivel
        mockMvc.perform(post("/usuarios/registro")
                        .contentType("application/json")
                        .content(json("Joao Clone", "joao.duplicado@teste.com", "654321")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("Email já cadastrado"));
    }

    @Test
    void registroComSenhaCurta_retorna400() throws Exception {
        mockMvc.perform(post("/usuarios/registro")
                        .contentType("application/json")
                        .content(json("Ana Doadora", "ana.senhacurta@teste.com", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A senha deve ter ao menos 6 caracteres"));
    }

    @Test
    void registroComEmailInvalido_retorna400() throws Exception {
        mockMvc.perform(post("/usuarios/registro")
                        .contentType("application/json")
                        .content(json("Ana Doadora", "email-invalido", "123456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Email inválido"));
    }
}
