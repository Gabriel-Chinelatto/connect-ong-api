package com.example.connectong_api;

import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.service.JwtService;
import com.example.connectong_api.service.ProvedorIA;

import org.junit.jupiter.api.BeforeEach;
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
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private JwtService jwtService;

    // Substitui o GroqService por um mock: controlamos disponivel()/completar().
    @MockBean private ProvedorIA provedorIA;

    private static final AtomicLong SEQ = new AtomicLong(1);

    private Long ongLimeiraId;
    private Long necessidadeRoupasId;
    private Long necessidadeRoupasCampinasId;

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

        // ONG em Campinas, tambem com uma necessidade de Roupas (para provar que a
        // cidade CITADA na mensagem/bairro vence a cidade "de fora" - Limeira).
        Ong casaCampinas = new Ong("Casa Solidaria " + n, "casacamp" + n + "@assist.test",
                "1932332000", "Campinas", "Apoia familias.");
        casaCampinas = ongRepository.save(casaCampinas);
        Necessidade agasalhos = new Necessidade();
        agasalhos.setOng(casaCampinas);
        agasalhos.setTitulo("Agasalhos adultos " + n);
        agasalhos.setDescricao("Casacos e blusas de frio.");
        agasalhos.setCategoria("Roupas");
        agasalhos.setUrgente(false);
        agasalhos = necessidadeRepository.save(agasalhos);
        necessidadeRoupasCampinasId = agasalhos.getId();

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
        Mockito.when(provedorIA.completar(Mockito.anyList(), Mockito.any()))
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
        Mockito.when(provedorIA.completar(Mockito.anyList(), Mockito.any()))
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
        Mockito.when(provedorIA.completar(Mockito.anyList(), Mockito.any()))
                .thenReturn(Optional.empty()); // simula timeout/429

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho roupas pra doar em Limeira\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.sugestoes", not(empty())));
    }

    // ---------------------------------------------------------------
    // (1) LOCALIZACAO ADAPTAVEL: cidade CITADA na mensagem vence a de fora.
    // Body diz "Limeira", a mensagem diz "Campinas" -> prioriza Campinas.
    // ---------------------------------------------------------------
    @Test
    void localizacao_cidadeCitadaNaMensagem_venceCidadeDeFora() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho roupas para doar em Campinas\","
                                + "\"cidade\":\"Limeira\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                // a necessidade de Roupas de CAMPINAS vem primeiro (a mensagem venceu
                // a cidade "Limeira" enviada no body). O subtitulo do card traz a cidade.
                .andExpect(jsonPath("$.sugestoes[0].subtitulo", containsString("Campinas")))
                .andExpect(jsonPath("$.sugestoes[*].id",
                        hasItem(necessidadeRoupasCampinasId.intValue())));
    }

    // ---------------------------------------------------------------
    // (1b) BAIRRO conhecido "Barao Geraldo" -> Campinas.
    // ---------------------------------------------------------------
    @Test
    void localizacao_bairroBaraoGeraldo_mapeiaParaCampinas() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho roupas, moro no Barao Geraldo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                // bairro mapeou para Campinas -> a necessidade de Campinas vem primeiro
                .andExpect(jsonPath("$.sugestoes[0].subtitulo", containsString("Campinas")))
                .andExpect(jsonPath("$.sugestoes[*].id",
                        hasItem(necessidadeRoupasCampinasId.intValue())));
    }

    // ---------------------------------------------------------------
    // (3) SANITIZACAO: id vazado pela IA no texto sai limpo da resposta.
    // ---------------------------------------------------------------
    @Test
    void sanitizacao_idNoTextoDaIa_saiLimpo() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        String json = "{\"resposta\":\"A [id=5] Casa Renascer precisa de cobertores id=5.\","
                + "\"sugestoes\":[{\"tipo\":\"NECESSIDADE\",\"id\":" + necessidadeRoupasId + "}]}";
        Mockito.when(provedorIA.completar(Mockito.anyList(), Mockito.any()))
                .thenReturn(Optional.of(json));

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho cobertores\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ia"))
                .andExpect(jsonPath("$.resposta", not(containsString("id="))))
                .andExpect(jsonPath("$.resposta", not(containsString("[id"))))
                // o card estruturado permanece intacto
                .andExpect(jsonPath("$.sugestoes[0].id").value(necessidadeRoupasId.intValue()));
    }

    // ---------------------------------------------------------------
    // (3b) FALLBACK por regras: prosa amigavel SEM ids, cards no estruturado.
    // ---------------------------------------------------------------
    @Test
    void fallbackRegras_prosaSemIds_cardsNoEstruturado() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho roupas pra doar em Limeira\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.resposta", not(containsString("id="))))
                .andExpect(jsonPath("$.resposta", not(containsString("[id"))))
                .andExpect(jsonPath("$.sugestoes", not(empty())))
                .andExpect(jsonPath("$.sugestoes[0].id", notNullValue()));
    }

    // ---------------------------------------------------------------
    // (5) VISAO: request com imagemBase64 roteia para o provedor de visao.
    // ---------------------------------------------------------------
    @Test
    void visao_comImagem_roteiaParaProvedorDeVisao() throws Exception {
        Mockito.when(provedorIA.visaoDisponivel()).thenReturn(true);
        String json = "{\"resposta\":\"Vejo cobertores na foto! A Lar Viva precisa deles.\","
                + "\"sugestoes\":[{\"tipo\":\"NECESSIDADE\",\"id\":" + necessidadeRoupasId + "}]}";
        Mockito.when(provedorIA.completarComImagem(Mockito.anyList(), Mockito.anyString()))
                .thenReturn(Optional.of(json));

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"o que faco com isto?\","
                                + "\"imagemBase64\":\"data:image/jpeg;base64,QUJD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ia"))
                .andExpect(jsonPath("$.sugestoes[0].id").value(necessidadeRoupasId.intValue()));

        // roteou para a VISAO (modelo multimodal), NAO para o completar de texto
        Mockito.verify(provedorIA).completarComImagem(Mockito.anyList(), Mockito.anyString());
        Mockito.verify(provedorIA, Mockito.never()).completar(Mockito.anyList());
        Mockito.verify(provedorIA, Mockito.never()).completar(Mockito.anyList(), Mockito.any());
    }

    // ---------------------------------------------------------------
    // (5b) VISAO falha (timeout/429) -> fallback amigavel "me conta em texto".
    // ---------------------------------------------------------------
    @Test
    void visao_falha_caiEmFallbackAmigavel() throws Exception {
        Mockito.when(provedorIA.visaoDisponivel()).thenReturn(true);
        Mockito.when(provedorIA.completarComImagem(Mockito.anyList(), Mockito.anyString()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"o que faco com isto?\","
                                + "\"imagemBase64\":\"data:image/jpeg;base64,QUJD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.resposta", containsString("texto")));
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

    // ===============================================================
    // NOVOS COMPORTAMENTOS (modo conversa, empty sugestoes, titulo,
    // visao nao-doavel, user-aware)
    // ===============================================================

    // (a) SEM IA + pergunta GERAL -> conversa, sugestoes VAZIAS (nao despeja cards).
    @Test
    void perguntaGeral_semIa_naoRetornaCards() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"qual a capital da Franca?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.sugestoes", empty()))
                .andExpect(jsonPath("$.resposta", not(emptyString())))
                // titulo derivado da 1a mensagem
                .andExpect(jsonPath("$.titulo", not(emptyOrNullString())));
    }

    // (a2) COM IA + pergunta geral: a IA devolve sugestoes vazias -> respeitamos
    // (NAO forcamos o fallback a preencher).
    @Test
    void perguntaGeral_comIa_sugestoesVaziasSaoRespeitadas() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        Mockito.when(provedorIA.completar(Mockito.anyList(), Mockito.any()))
                .thenReturn(Optional.of("{\"resposta\":\"A capital da Franca e Paris!\","
                        + "\"sugestoes\":[],\"titulo\":\"Capital da Franca\"}"));

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"qual a capital da Franca?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ia"))
                .andExpect(jsonPath("$.sugestoes", empty()))
                .andExpect(jsonPath("$.titulo").value("Capital da Franca"));
    }

    // (b) SEM IA + pergunta de DOACAO -> cards preenchidos.
    @Test
    void perguntaDeDoacao_semIa_retornaCards() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"quero doar roupas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.sugestoes", not(empty())))
                .andExpect(jsonPath("$.titulo", not(emptyOrNullString())));
    }

    // (c) VISAO de item NAO doavel: a IA descreve gentil e devolve sugestoes vazias
    // -> sem cards (respeitado).
    @Test
    void visao_itemNaoDoavel_semCards() throws Exception {
        Mockito.when(provedorIA.visaoDisponivel()).thenReturn(true);
        Mockito.when(provedorIA.completarComImagem(Mockito.anyList(), Mockito.anyString()))
                .thenReturn(Optional.of("{\"resposta\":\"Isso parece uma selfie, nao um "
                        + "item para doacao. Me mostra o que voce quer doar?\","
                        + "\"sugestoes\":[],\"titulo\":\"Foto de pessoa\"}"));

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"da pra doar isso?\","
                                + "\"imagemBase64\":\"data:image/jpeg;base64,QUJD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ia"))
                .andExpect(jsonPath("$.sugestoes", empty()))
                .andExpect(jsonPath("$.resposta", containsString("doar")));
    }

    // (d) USER-AWARE: doador autenticado com historico -> o system prompt injeta um
    // resumo DELE (categoria/ONG que ele ja ajudou). Capturamos as mensagens.
    @Test
    @SuppressWarnings("unchecked")
    void usuarioAutenticadoComHistorico_injetaResumoNoPrompt() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(true);
        Mockito.when(provedorIA.completar(Mockito.anyList(), Mockito.any()))
                .thenReturn(Optional.of("{\"resposta\":\"Voce costuma ajudar por aqui!\","
                        + "\"sugestoes\":[],\"titulo\":\"Meu historico\"}"));

        // Cria um DOADOR real e um match CONCLUIDO com a necessidade de Roupas.
        Usuario doador = new Usuario();
        doador.setNome("Maria Doadora");
        doador.setEmail("maria" + SEQ.getAndIncrement() + "@assist.test");
        doador.setTipo("DOADOR");
        doador.setCidade("Limeira");
        doador = usuarioRepository.save(doador);

        Necessidade nec = necessidadeRepository.findById(necessidadeRoupasId).orElseThrow();
        Interesse i = new Interesse();
        i.setDoador(doador);
        i.setNecessidade(nec);
        i.setStatus("CONCLUIDO");
        interesseRepository.save(i);

        String token = jwtService.gerarAccessToken(doador);

        mockMvc.perform(post("/assistente")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"mensagem\":\"quem sou eu? com base no que ja doei, "
                                + "onde posso doar?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("ia"));

        // O system prompt deve conter o resumo do doador (categoria Roupas + doador).
        ArgumentCaptor<List<ProvedorIA.MensagemIA>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(provedorIA).completar(captor.capture(), Mockito.any());
        String system = captor.getValue().stream()
                .filter(m -> "system".equals(m.papel()))
                .map(ProvedorIA.MensagemIA::conteudo)
                .findFirst().orElse("");
        org.junit.jupiter.api.Assertions.assertTrue(
                system.contains("DOADOR logado"),
                "o prompt deveria conter o bloco do doador logado");
        org.junit.jupiter.api.Assertions.assertTrue(
                system.contains("Roupas"),
                "o prompt deveria citar a categoria ja doada (Roupas)");
    }

    // (e) TITULO presente na resposta em modo regras (derivado da 1a mensagem).
    @Test
    void titulo_presenteNaResposta() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"tenho roupas pra doar em Limeira\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo", not(emptyOrNullString())));
    }

    // ===============================================================
    // LUGAR NA MENSAGEM (correcoes de 13/08: o campo cidade das ONGs
    // reais guarda "Cidade - UF" e o modo regras nao casava)
    // ===============================================================

    // (f) Bug visto na feira: "se eu estiver no rio de janeiro" devolvia ONGs
    // aleatorias do Brasil, porque "rio de janeiro" nunca casava com o campo
    // "Rio de Janeiro - RJ". Agora a parte-cidade e comparada em separado.
    @Test
    void semChave_cidadeComUfNoCampo_eEncontradaPelaMensagem() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);
        long n = SEQ.getAndIncrement();
        Ong rio = new Ong("Instituto Carioca " + n, "rio" + n + "@assist.test",
                "2122223000", "Rio de Janeiro - RJ", "Apoia comunidades.");
        rio.setVerificada(true);
        rio = ongRepository.save(rio);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"se eu estiver no rio de janeiro, onde posso doar?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.resposta", containsString("Rio de Janeiro")))
                .andExpect(jsonPath("$.sugestoes[*].id", hasItem(rio.getId().intValue())));
    }

    // (f2) Mensagem que se APRESENTA com um lugar ("sou de campinas") e
    // continuacao da conversa de doacao: responde com ONGs de la, nao com o
    // "nao vou saber responder" conversacional.
    @Test
    void semChave_soCitarACidade_respondeComOngsDeLa() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"sou de campinas\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.resposta",
                        not(containsString("nao vou saber responder"))))
                .andExpect(jsonPath("$.sugestoes", not(empty())));
    }

    // (f3) Estado por extenso ("no parana") filtra pela UF do campo "Cidade - UF".
    @Test
    void semChave_estadoPorExtenso_filtraPelaUf() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);
        long n = SEQ.getAndIncrement();
        Ong curitiba = new Ong("Acao Curitibana " + n, "cwb" + n + "@assist.test",
                "4133334000", "Curitiba - PR", "Apoio a familias.");
        curitiba.setVerificada(true);
        curitiba = ongRepository.save(curitiba);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"quero doar para quem esta no parana\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.resposta", containsString("Parana")))
                .andExpect(jsonPath("$.sugestoes[*].id", hasItem(curitiba.getId().intValue())));
    }

    // (f4) Pergunta GERAL que por acaso contem nome de cidade ("qual a capital
    // da Franca?" — existe Franca-SP) continua CONVERSACIONAL, sem cards: a
    // resposta por lugar exige o marcador de localizacao ("sou de", "moro"...).
    @Test
    void semChave_perguntaGeralComNomeDeCidade_naoDespejaCards() throws Exception {
        Mockito.when(provedorIA.disponivel()).thenReturn(false);
        long n = SEQ.getAndIncrement();
        Ong franca = new Ong("Solidariedade Francana " + n, "franca" + n + "@assist.test",
                "1637223000", "Franca - SP", "Apoio local.");
        ongRepository.save(franca);

        mockMvc.perform(post("/assistente")
                        .contentType("application/json")
                        .content("{\"mensagem\":\"qual a capital da Franca?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modo").value("regras"))
                .andExpect(jsonPath("$.sugestoes", empty()));
    }
}
