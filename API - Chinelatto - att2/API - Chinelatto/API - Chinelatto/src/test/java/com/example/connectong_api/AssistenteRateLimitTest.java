package com.example.connectong_api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rate limiting do assistente (POST /assistente): protege a cota gratuita da IA.
 *
 * O properties compartilhado dos testes desliga o limite (IP 127.0.0.1 e comum a
 * suite); aqui ele volta a um valor real (3) via @TestPropertySource, num contexto
 * proprio e com um IP dedicado, para nao interferir nos demais testes.
 * Sem chave da Groq, o endpoint responde por regras — mas o limite e checado ANTES,
 * entao nao dependemos da IA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.ia.ratelimit.max=3"
})
class AssistenteRateLimitTest {

    private static final String MSG_429 = "Muitas tentativas. Tente novamente em alguns minutos.";

    @Autowired private MockMvc mockMvc;

    private static RequestPostProcessor ip(String addr) {
        return request -> {
            request.setRemoteAddr(addr);
            return request;
        };
    }

    @Test
    void aQuartaMensagemDoMesmoIp_retorna429() throws Exception {
        String corpo = "{\"mensagem\":\"tenho roupas pra doar\"}";

        // 3 mensagens -> 200
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/assistente")
                            .with(ip("10.8.7.6"))
                            .contentType("application/json")
                            .content(corpo))
                    .andExpect(status().isOk());
        }

        // 4a mensagem do MESMO IP -> 429
        mockMvc.perform(post("/assistente")
                        .with(ip("10.8.7.6"))
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.erro").value(MSG_429));

        // outro IP nao esta bloqueado (chave por IP)
        mockMvc.perform(post("/assistente")
                        .with(ip("10.8.7.99"))
                        .contentType("application/json")
                        .content(corpo))
                .andExpect(status().isOk());
    }
}
