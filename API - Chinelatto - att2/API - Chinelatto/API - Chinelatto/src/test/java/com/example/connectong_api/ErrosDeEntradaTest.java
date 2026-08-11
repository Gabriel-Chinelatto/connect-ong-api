package com.example.connectong_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Erro do CLIENTE nao pode virar erro de SERVIDOR.
 *
 * Achado na varredura de 10/08/2026, testando a API de producao: varias
 * chamadas mal formadas respondiam **500 "Ocorreu um erro inesperado no
 * servidor"** — por exemplo `/favoritos/ids` sem o parametro `usuarioId`, ou
 * `/usuarios/me` (rota que nao existe; a correta e `/auth/me`).
 *
 * Isso engana quem consome a API, esconde o erro real do usuario e enche o
 * monitoramento de falso alarme de servidor quebrado.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // sem o filtro JWT: aqui interessa o tratamento de erro
class ErrosDeEntradaTest {

    @Autowired private MockMvc mvc;

    @Test
    void parametroObrigatorioAusente_devolve400ComONomeDoParametro() throws Exception {
        mvc.perform(get("/favoritos/ids"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value(
                        org.hamcrest.Matchers.containsString("usuarioId")));
    }

    @Test
    void parametroComTipoErrado_devolve400() throws Exception {
        // /ongs/{id} espera um numero
        mvc.perform(get("/ongs/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    void metodoHttpErrado_devolve405() throws Exception {
        // /usuarios/login existe, mas so aceita POST. Antes: 500.
        mvc.perform(get("/usuarios/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.erro").value(
                        org.hamcrest.Matchers.containsString("GET")));
    }

    @Test
    void rotaInexistente_devolve404() throws Exception {
        mvc.perform(get("/rota-que-nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nenhumErroDeEntrada_podeVirar500() throws Exception {
        // A mensagem generica de 500 nunca deve aparecer nestes casos.
        mvc.perform(get("/favoritos/ids"))
                .andExpect(jsonPath("$.erro").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("inesperado"))));
    }
}
