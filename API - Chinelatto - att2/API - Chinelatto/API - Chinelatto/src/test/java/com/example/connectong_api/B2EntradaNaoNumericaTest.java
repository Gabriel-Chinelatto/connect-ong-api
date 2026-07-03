package com.example.connectong_api;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug B2: POST /favoritos e POST /campanhas/{id}/contribuir faziam
 * Long/Double.valueOf sobre um Map cru — qualquer entrada nao numerica
 * estourava NumberFormatException e virava 500. Com os DTOs tipados
 * (FavoritoRequestDTO / ContribuicaoRequestDTO) a mesma entrada vira 400
 * com mensagem legivel, e o contrato (nomes de campo) continua identico.
 */
@SpringBootTest
@AutoConfigureMockMvc
class B2EntradaNaoNumericaTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    private String token(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNome("Doador " + id);
        u.setTipo("DOADOR");
        return jwtService.gerarAccessToken(u);
    }

    @Test
    void favoritos_usuarioIdNaoNumerico_retorna400_naoMais500() throws Exception {
        mockMvc.perform(post("/favoritos")
                        .header("Authorization", "Bearer " + token(2001L))
                        .contentType("application/json")
                        .content("{\"usuarioId\":\"abc\",\"tipo\":\"ONG\",\"alvoId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void favoritos_alvoIdNaoNumerico_retorna400_naoMais500() throws Exception {
        mockMvc.perform(post("/favoritos")
                        .header("Authorization", "Bearer " + token(2001L))
                        .contentType("application/json")
                        .content("{\"usuarioId\":2001,\"tipo\":\"ONG\",\"alvoId\":\"xyz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void favoritos_semUsuarioId_retorna400ComMensagemDeCampo() throws Exception {
        mockMvc.perform(post("/favoritos")
                        .header("Authorization", "Bearer " + token(2001L))
                        .contentType("application/json")
                        .content("{\"tipo\":\"ONG\",\"alvoId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Informe o usuarioId"));
    }

    @Test
    void favoritos_contratoValidoContinuaFuncionando() throws Exception {
        // mesmo payload que os apps enviam hoje (id numerico ou string numerica)
        mockMvc.perform(post("/favoritos")
                        .header("Authorization", "Bearer " + token(2002L))
                        .contentType("application/json")
                        .content("{\"usuarioId\":2002,\"tipo\":\"ONG\",\"alvoId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.favorito").value(true));

        // string numerica (Jackson converte) tambem segue aceita — compat total
        mockMvc.perform(post("/favoritos")
                        .header("Authorization", "Bearer " + token(2002L))
                        .contentType("application/json")
                        .content("{\"usuarioId\":\"2002\",\"tipo\":\"CAMPANHA\",\"alvoId\":\"3\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void contribuir_valorNaoNumerico_retorna400_naoMais500() throws Exception {
        mockMvc.perform(post("/campanhas/1/contribuir")
                        .header("Authorization", "Bearer " + token(2001L))
                        .contentType("application/json")
                        .content("{\"valor\":\"abc\",\"doadorNome\":\"Maria\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void contribuir_valorNegativoOuAusente_retorna400ComMensagem() throws Exception {
        mockMvc.perform(post("/campanhas/1/contribuir")
                        .header("Authorization", "Bearer " + token(2001L))
                        .contentType("application/json")
                        .content("{\"valor\":-10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O valor deve ser maior que zero"));

        mockMvc.perform(post("/campanhas/1/contribuir")
                        .header("Authorization", "Bearer " + token(2001L))
                        .contentType("application/json")
                        .content("{\"doadorNome\":\"Maria\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("O valor deve ser maior que zero"));
    }

    @Test
    void contribuir_payloadValido_passaDaValidacao() throws Exception {
        // corpo valido em campanha inexistente -> 404 (ou seja, a tipagem nao
        // barrou o contrato atual; a validacao aconteceu e o fluxo prosseguiu)
        mockMvc.perform(post("/campanhas/999999/contribuir")
                        .header("Authorization", "Bearer " + token(2001L))
                        .contentType("application/json")
                        .content("{\"valor\":25.5,\"doadorNome\":\"Maria\"}"))
                .andExpect(status().isNotFound());
    }
}
