package com.example.connectong_api;

import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.service.ProvedorIA;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do assistente de doacao (POST /assistente), via MockMvc/H2.
 *
 * O provedor de IA e MOCKADO ({@link MockBean}) — NUNCA chamamos a Groq de
 * verdade (a CI nao tem chave). Assim exercitamos os dois caminhos:
 *  - IA indisponivel/falha  -> FALLBACK por regras (modo "regras");
 *  - IA disponivel          -> parse do JSON da IA e do texto puro (modo "ia").
 */
@SpringBootTest
@AutoConfigureMockMvc
class AssistenteTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;

    // Substitui o GroqService por um mock: controlamos disponivel()/completar().
    @MockBean private ProvedorIA provedorIA;

    private static final AtomicLong SEQ = new AtomicLong(1);

    private Long ongLimeiraId;
    private Long necessidadeRoupasId;

    @BeforeEach
    void seed() {
        long n = SEQ.getAndIncrement();

        // ONG verificada em Limeira, com uma necessidade de Roupas (urgente).
        Ong larViva = new Ong("Lar Viva " + n, "larviva" + n + "@assist.test",
                "1934411000", "Limeira", "Acolhe idosos.");
        larViva.setVerificada(true);
        larViva = ongRepository.save(larViva);
        ongLimeiraId = larViva.getId();

        Necessidade cobertores = new Necessidade();
        cobertores.setOng(larViva);
        cobertores.setTitulo("Cobertores de inverno " + n);
        cobertores.setDescricao("Cobertores novos ou usados em bom estado.");
        cobertores.setCategoria("Roupas");
        cobertores.setUrgente(true);
        cobertores = necessidadeRepository.save(cobertores);
        necessidadeRoupasId = cobertores.getId();

        // Uma ONG/necessidade de outra cidade e categoria (ruido para a busca).
        Ong outra = new Ong("Abrigo Patinhas " + n, "patinhas" + n + "@assist.test",
                "1934223000", "Piracicaba", "Resgate de animais.");
        outra = ongRepository.save(outra);
        Necessidade racao = new Necessidade();
        racao.setOng(outra);
        racao.setTitulo("Racao para caes " + n);
        racao.setCategoria("Alimentos");
        racao.setUrgente(false);
        necessidadeRepository.save(racao);
    }

    // ---------------------------------------------------------------
    // (a) SEM IA -> fallback por regras com sugestoes reais
    // ---------------------------------------------------------------
    @Test
    void semChave_fallbackPorRegras_sugereNecessidadeDeRoupasNaCidade() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho roupas pra doar em Limeira\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.sugestoes", not(empty())))
                // a necessidade de Roupas em Limeira aparece como card real
                .andExpect(jsonPath("$.sugestoes[*].id",
                        hasItem(necessidadeRoupasId.intValue())))
                .andExpect(jsonPath("$.sugestoes[*].tipo", hasItem("NECESSIDADE")));
    }

    // ---------------------------------------------------------------
    // (a2) SEM IA -> "o que a ONG X precisa" resolve pela ONG citada
    // ---------------------------------------------------------------
    @Test
    void semChave_perguntaSobreOngEspecifica_listaNecessidadesDaOng() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);
        Ong ong = ongRepository.findById(ongLimeiraId).orElseThrow();

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"o que a " + ong.getNome() + " precisa?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.sugestoes[*].id",
                        hasItem(necessidadeRoupasId.intValue())));
    }

    // ---------------------------------------------------------------
    // (b) IA disponivel + resposta JSON valida -> modo "ia", ids validados
    // ---------------------------------------------------------------
    @Test
    void comIa_respostaJson_viraRespostaMaisSugestoesReais() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        String json = "{\"resposta\":\"Que legal! A Lar Viva precisa de cobertores.\","
                + "\"sugestoes\":[{\"tipo\":\"NECESSIDADE\",\"id\":" + necessidadeRoupasId + "}]}";
        Mockito.when(provedorIA.completar(Mockito.anyList()))
                .thenReturn(Optional.of("Aqui esta: " + json));

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho cobertores\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ia"))
                .andExpect(jsonPath("$.resposta")
                        .value("Que legal! A Lar Viva precisa de cobertores."))
                .andExpect(jsonPath("$.sugestoes[0].id").value(necessidadeRoupasId.intValue()))
                .andExpect(jsonPath("$.sugestoes[0].tipo").value("NECESSIDADE"))
                // titulo/subtitulo vem dos dados REAIS, nao do que a IA mandou
                .andExpect(jsonPath("$.sugestoes[0].titulo", containsString("Cobertores")));
    }

    // ---------------------------------------------------------------
    // (b2) IA disponivel + texto NAO-JSON -> texto puro + sugestoes derivadas
    // ---------------------------------------------------------------
    @Test
    void comIa_textoPuro_usaTextoComoRespostaEDerivaSugestoes() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        Mockito.when(provedorIA.completar(Mockito.anyList()))
                .thenReturn(Optional.of("Voce pode doar suas roupas para quem mais precisa!"));

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho roupas em Limeira\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ia"))
                .andExpect(jsonPath("$.resposta")
                        .value("Voce pode doar suas roupas para quem mais precisa!"))
                // derivadas por regras a partir da mensagem do usuario
                .andExpect(jsonPath("$.sugestoes[*].id",
                        hasItem(necessidadeRoupasId.intValue())));
    }

    // ---------------------------------------------------------------
    // (b3) IA disponivel mas FALHA (timeout/429/rede) -> cai no fallback "regras"
    // ---------------------------------------------------------------
    @Test
    void comIa_masFalha_caiNoFallbackRegras() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        Mockito.when(provedorIA.completar(Mockito.anyList()))
                .thenReturn(Optional.empty()); // simula timeout/429

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho roupas pra doar em Limeira\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.sugestoes", not(empty())));
    }

    // ---------------------------------------------------------------
    // (d) mensagem vazia -> 400 (validacao @NotBlank)
    // ---------------------------------------------------------------
    @Test
    void mensagemVazia_retorna400() throws Exception {
        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mensagemAusente_retorna400() throws Exception {
        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
