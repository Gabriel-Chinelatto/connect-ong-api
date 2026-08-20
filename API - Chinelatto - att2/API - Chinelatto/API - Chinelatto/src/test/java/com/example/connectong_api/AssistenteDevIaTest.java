package com.example.connectong_api;

import com.example.connectong_api.service.ProvedorIA;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do assistente "Sobre o Desenvolvimento" (POST /assistente-dev) e do
 * diagnostico da IA (GET /ia/status).
 *
 * Nasceram da rodada de 2026-08-20, quando a IA apareceu "muito limitada" na
 * apresentacao: a Groq tinha aposentado o modelo de texto, TUDO caiu no
 * fallback por regras — e ate uma pergunta central ("matches") era recusada
 * como fora de escopo por causa do plural.
 *
 * O provedor de IA e MOCKADO: a CI nao tem chave e nunca chama a Groq.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistenteDevIaTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ProvedorIA provedorIA;

    private static final String FORA_DE_ESCOPO = "Eu só falo sobre como o Connect ONG foi desenvolvido";

    // ---------------------------------------------------------------
    // (a) Fallback por regras: plural nao pode virar "fora de escopo"
    // ---------------------------------------------------------------

    /**
     * "matches" e o coracao do produto e tem secao propria no documento
     * ("Como funciona o MATCH"), mas o casamento era literal: "matches" nao
     * continha "match", a secao tirava zero e o assistente respondia que
     * aquilo nao era assunto dele. Foi o que o usuario viu na tela.
     */
    @Test
    void semIa_perguntaNoPlural_encontraASecaoEmVezDeRecusar() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente-dev")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"matches\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.resposta", not(containsString(FORA_DE_ESCOPO))))
                .andExpect(jsonPath("$.resposta", containsStringIgnoringCase("interesse")));
    }

    /** A recusa educada continua valendo para o que realmente nao e do projeto. */
    @Test
    void semIa_perguntaForaDeEscopo_continuaRecusando() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente-dev")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"qual seu time de futebol favorito?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.resposta", containsString(FORA_DE_ESCOPO)));
    }

    // ---------------------------------------------------------------
    // (b) Com IA: contexto enxuto (a cota gratuita e por MINUTO)
    // ---------------------------------------------------------------

    /**
     * O prompt levava o documento inteiro (~18 KB / ~5.000 tokens) em toda
     * pergunta, e o tier gratuito da 8.000 tokens por MINUTO: a segunda
     * pergunta seguida ja caia no "Modo basico". Agora vao o INDICE dos
     * assuntos + so os trechos relevantes.
     */
    @Test
    @SuppressWarnings("unchecked")
    void comIa_mandaIndiceMaisTrechosRelevantes_naoODocumentoInteiro() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        Mockito.when(provedorIA.completar(Mockito.anyList(), Mockito.any()))
                .thenReturn(Optional.of("O match nasce com status PENDENTE."));

        mockMvc.perform(post("/assistente-dev")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"como funciona o match?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ia"));

        ArgumentCaptor<List<ProvedorIA.MensagemIA>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(provedorIA).completar(captor.capture(), Mockito.any());

        String system = captor.getValue().stream()
                .filter(m -> "system".equals(m.papel()))
                .map(ProvedorIA.MensagemIA::conteudo)
                .findFirst().orElseThrow();

        // O indice cita assuntos que NAO sao o da pergunta (a IA precisa saber
        // que existem, para nao dizer que estao fora de escopo)...
        assertTrue(system.contains("Simulador de frete"),
                "o indice dos assuntos deve ir no prompt");
        // ...e o trecho da pergunta veio junto.
        assertTrue(system.contains("PENDENTE"),
                "a secao do match deveria estar entre os trechos relevantes");
        // ...mas o documento INTEIRO nao: o prompt tem que ser bem menor.
        assertTrue(system.length() < 9000,
                "prompt grande demais (" + system.length() + " chars): a cota da Groq e por minuto");
    }

    /** Continuacao de conversa ("e como isso e testado?") ainda acha o assunto. */
    @Test
    @SuppressWarnings("unchecked")
    void comIa_perguntaDeContinuacao_usaOHistoricoParaEscolherOsTrechos() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        Mockito.when(provedorIA.completar(Mockito.anyList(), Mockito.any()))
                .thenReturn(Optional.of("Sao 179 testes no backend."));

        mockMvc.perform(post("/assistente-dev")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"e como isso é testado?\","
                                + "\"historico\":[{\"papel\":\"usuario\",\"texto\":\"fale do rate limiting\"}]}"))
                .andExpect(status().isOk());

        ArgumentCaptor<List<ProvedorIA.MensagemIA>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(provedorIA).completar(captor.capture(), Mockito.any());
        String system = captor.getValue().stream()
                .filter(m -> "system".equals(m.papel()))
                .map(ProvedorIA.MensagemIA::conteudo)
                .findFirst().orElseThrow();

        assertTrue(system.toLowerCase().contains("rate limiting"),
                "a pergunta anterior deveria ter entrado na escolha dos trechos");
    }

    // ---------------------------------------------------------------
    // (c) Diagnostico da IA
    // ---------------------------------------------------------------

    /**
     * GET /ia/status e publico de proposito (da para conferir do celular, no
     * Render, antes de apresentar) — entao nao pode vazar a chave.
     */
    @Test
    void status_ehPublico_eNuncaExpoeAChave() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        Mockito.when(provedorIA.modelos()).thenReturn(List.of("openai/gpt-oss-120b", "openai/gpt-oss-20b"));

        mockMvc.perform(get("/ia/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveConfigurada").value(true))
                .andExpect(jsonPath("$.modelos[0]").value("openai/gpt-oss-120b"))
                .andExpect(content().string(not(containsString("gsk_"))));
    }

    /** Sem chave, o status diz na cara que a IA esta em modo regras. */
    @Test
    void status_semChave_avisaQueEstaEmRegras() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(get("/ia/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chaveConfigurada").value(false))
                .andExpect(jsonPath("$.modo", containsString("regras")));
    }
}
