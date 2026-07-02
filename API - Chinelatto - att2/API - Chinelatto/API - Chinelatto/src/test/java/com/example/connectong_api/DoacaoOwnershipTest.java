package com.example.connectong_api;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que "Minhas Doacoes" e por usuario: um item criado pelo doador A aparece
 * em GET /doacoes/minhas de A, mas NAO no de B. Antes da Fase 5, /doacoes era um
 * catalogo global (mesma lista para todos) — este teste falharia.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DoacaoOwnershipTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String token(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Doador " + id);
        u.setTipo("DOADOR");
        return jwtService.gerarAccessToken(u);
    }

    @Test
    void minhasDoacoes_saoIsoladasPorUsuario() throws Exception {
        final String tokenA = token(1001L);
        final String tokenB = token(1002L);

        // A cadastra um item (o dono vem do token, nao do corpo).
        mockMvc.perform(post("/doacoes")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType("application/json")
                        .content("{\"nome\":\"CestaDoA\",\"descricao\":\"basica\","
                                + "\"quantidade\":5,\"categoria\":\"Alimento\","
                                + "\"tipo\":\"Nova\",\"urgente\":false,\"novo\":true}"))
                .andExpect(status().isOk());

        // Aparece em "minhas" de A...
        mockMvc.perform(get("/doacoes/minhas")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("CestaDoA")));

        // ...mas NAO em "minhas" de B.
        mockMvc.perform(get("/doacoes/minhas")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", not(hasItem("CestaDoA"))));
    }
}
