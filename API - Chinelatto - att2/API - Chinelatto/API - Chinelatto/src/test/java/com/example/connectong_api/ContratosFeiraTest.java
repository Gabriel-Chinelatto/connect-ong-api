package com.example.connectong_api;

import com.example.connectong_api.model.Campanha;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.AvaliacaoDoadorRepository;
import com.example.connectong_api.repository.CampanhaRepository;
import com.example.connectong_api.repository.DoacaoFinanceiraRepository;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.NotificacaoRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contratos da feira (A-G), exercitados via HTTP (MockMvc) com H2:
 *  A) PIX simulado em 2 fases (gerar-codigo stateless + doacao com campanha);
 *  B) match CONCLUIDO (so a ONG dona, so de ACEITO, notifica o doador);
 *  C) prestacao rica (fotos/valor, permitida em CONCLUIDO) + pendencias com
 *     prazo de 10 dias e penalidade no score de transparencia;
 *  D) avaliacao de doador (upsert por ong+doador) + perfil publico do doador
 *     (sem email/telefone/valores);
 *  E) fotos como base64 no perfil da ONG (capa/endereco/fotosLocal);
 *  F) chat com anexo (texto vazio ok) + status com ultimoVistoEpoch/online;
 *  G) streak do Top 1 do ranking (diasNoTopo / ultimoReinadoDias).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContratosFeiraTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder encoder;

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private DoacaoFinanceiraRepository doacaoFinanceiraRepository;
    @Autowired private AvaliacaoDoadorRepository avaliacaoDoadorRepository;
    @Autowired private NotificacaoRepository notificacaoRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ===================== helpers de seed =====================

    private Usuario criarDoador(String nome) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail("doador" + SEQ.getAndIncrement() + "@feira.test");
        u.setSenha(encoder.encode("123456"));
        u.setTipo("DOADOR");
        u.setCidade("Campinas");
        u.setEstado("SP");
        return usuarioRepository.save(u);
    }

    private Ong criarOng(String nome) {
        Ong ong = new Ong(nome, "ong" + SEQ.getAndIncrement() + "@feira.test",
                "1999999999", "Campinas", "ONG de teste");
        return ongRepository.save(ong);
    }

    private Usuario criarContaOng(Ong ong) {
        Usuario u = new Usuario();
        u.setNome(ong.getNome());
        u.setEmail("conta" + SEQ.getAndIncrement() + "@feira.test");
        u.setSenha(encoder.encode("123456"));
        u.setTipo("ONG");
        u.setOngId(ong.getId());
        return usuarioRepository.save(u);
    }

    private Necessidade criarNecessidade(Ong ong, String titulo) {
        Necessidade n = new Necessidade();
        n.setOng(ong);
        n.setTitulo(titulo);
        n.setDescricao("desc");
        n.setCategoria("Alimentos");
        return necessidadeRepository.save(n);
    }

    private Interesse criarInteresse(Necessidade nec, Usuario doador, String status) {
        Interesse i = new Interesse();
        i.setNecessidade(nec);
        i.setDoador(doador);
        i.setStatus(status);
        return interesseRepository.save(i);
    }

    private Campanha criarCampanha(Ong ong, String titulo, double meta) {
        Campanha c = new Campanha();
        c.setOng(ong);
        c.setTitulo(titulo);
        c.setDescricao("desc");
        c.setMetaValor(meta);
        c.setValorArrecadado(0.0);
        return campanhaRepository.save(c);
    }

    private String token(Usuario u) {
        return jwtService.gerarAccessToken(u);
    }

    // ===================== A) PIX em 2 fases =====================

    @Test
    void gerarCodigo_devolveCodigoPix_semPersistirNada() throws Exception {
        Usuario doador = criarDoador("Gerador");
        long antes = doacaoFinanceiraRepository.count();

        mockMvc.perform(post("/doacoes-financeiras/gerar-codigo")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"valor\": 50.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigoPix", startsWith("00020126SIMULADO")));

        // STATELESS: nada foi persistido na fase 1.
        assertEquals(antes, doacaoFinanceiraRepository.count());
    }

    @Test
    void gerarCodigo_valorInvalido_400() throws Exception {
        Usuario doador = criarDoador("GeradorInvalido");
        mockMvc.perform(post("/doacoes-financeiras/gerar-codigo")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"valor\": -1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doacao_comCodigoDaFase1_eCampanha_incrementaEAutoEncerra() throws Exception {
        Ong ong = criarOng("ONG Campanha PIX");
        criarContaOng(ong);
        Usuario doador = criarDoador("DoadorPix");
        Campanha campanha = criarCampanha(ong, "Campanha da Feira", 100.0);

        String codigo = "00020126SIMULADOCODIGODAFASE1XYZ9900";

        mockMvc.perform(post("/doacoes-financeiras")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"ongId\":" + ong.getId()
                                + ",\"doadorId\":" + doador.getId()
                                + ",\"valor\":100.0"
                                + ",\"codigoPix\":\"" + codigo + "\""
                                + ",\"campanhaId\":" + campanha.getId() + "}"))
                .andExpect(status().isCreated())
                // reaproveita o codigo da fase 1 (nao gera outro)
                .andExpect(jsonPath("$.codigoPix").value(codigo))
                .andExpect(jsonPath("$.campanhaId").value(campanha.getId()))
                .andExpect(jsonPath("$.campanhaTitulo").value("Campanha da Feira"))
                .andExpect(jsonPath("$.status").value("CONFIRMADO"));

        Campanha atualizada = campanhaRepository.findById(campanha.getId()).orElseThrow();
        assertEquals(100.0, atualizada.getValorArrecadado());
        // bateu a meta => auto-encerra
        assertTrue(atualizada.getEncerrada());
    }

    @Test
    void doacao_semCampanha_temCampanhaNula_eGeraCodigoQuandoNaoVeio() throws Exception {
        Ong ong = criarOng("ONG PIX Simples");
        Usuario doador = criarDoador("DoadorSimples");

        mockMvc.perform(post("/doacoes-financeiras")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"ongId\":" + ong.getId()
                                + ",\"doadorId\":" + doador.getId()
                                + ",\"valor\":25.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoPix", startsWith("00020126SIMULADO")))
                .andExpect(jsonPath("$.campanhaId").value(nullValue()))
                .andExpect(jsonPath("$.campanhaTitulo").value(nullValue()));
    }

    @Test
    void doacao_campanhaDeOutraOng_400() throws Exception {
        Ong ong = criarOng("ONG A Pix");
        Ong outra = criarOng("ONG B Pix");
        Usuario doador = criarDoador("DoadorCampErrada");
        Campanha campanhaDaOutra = criarCampanha(outra, "Campanha Alheia", 500.0);

        mockMvc.perform(post("/doacoes-financeiras")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"ongId\":" + ong.getId()
                                + ",\"doadorId\":" + doador.getId()
                                + ",\"valor\":10.0,\"campanhaId\":"
                                + campanhaDaOutra.getId() + "}"))
                .andExpect(status().isBadRequest());
    }

    // ===================== B) Match CONCLUIDO =====================

    @Test
    void concluir_porOutraOng_403() throws Exception {
        Ong dona = criarOng("ONG Dona Concluir");
        criarContaOng(dona);
        Ong intrusa = criarOng("ONG Intrusa Concluir");
        Usuario contaIntrusa = criarContaOng(intrusa);
        Usuario doador = criarDoador("DoadorConcluir403");
        Interesse match = criarInteresse(
                criarNecessidade(dona, "Cobertores"), doador, "ACEITO");

        mockMvc.perform(put("/interesses/" + match.getId() + "/concluir")
                        .header("Authorization", "Bearer " + token(contaIntrusa)))
                .andExpect(status().isForbidden());
    }

    @Test
    void concluir_soDeAceito_gravaDataEnotificaDoador() throws Exception {
        Ong ong = criarOng("ONG Concluir OK");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorConcluirOk");
        Necessidade nec = criarNecessidade(ong, "Fraldas G");
        Interesse pendente = criarInteresse(nec, doador, "PENDENTE");

        // PENDENTE nao pode ser concluido
        mockMvc.perform(put("/interesses/" + pendente.getId() + "/concluir")
                        .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isBadRequest());

        Interesse aceito = criarInteresse(
                criarNecessidade(ong, "Leite em po"), doador, "ACEITO");

        mockMvc.perform(put("/interesses/" + aceito.getId() + "/concluir")
                        .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"))
                .andExpect(jsonPath("$.dataConclusao").isNotEmpty());

        // as listagens de /interesses passam a incluir dataConclusao
        mockMvc.perform(get("/interesses?doadorId=" + doador.getId())
                        .header("Authorization", "Bearer " + token(doador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + aceito.getId()
                        + ")].dataConclusao").isNotEmpty());

        // notificacao ao doador com o texto do contrato
        boolean notificado = notificacaoRepository
                .findByUsuarioIdOrderByDataCriacaoDesc(doador.getId()).stream()
                .anyMatch(n -> n.getMensagem() != null
                        && n.getMensagem().contains("foi marcada como recebida pela "
                                + ong.getNome()));
        assertTrue(notificado, "doador deve ser notificado da conclusao");
    }

    // ===================== C) Prestacao rica + pendencias =====================

    @Test
    void prestacao_emMatchConcluido_comFotosEValor_ok() throws Exception {
        Ong ong = criarOng("ONG Prestadora");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorPrestacao");
        Necessidade nec = criarNecessidade(ong, "Cestas basicas");
        Interesse match = criarInteresse(nec, doador, "CONCLUIDO");
        match.setDataConclusao(LocalDateTime.now());
        interesseRepository.save(match);

        mockMvc.perform(post("/prestacoes")
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"interesseId\":" + match.getId()
                                + ",\"titulo\":\"Cestas entregues\""
                                + ",\"descricao\":\"20 familias atendidas\""
                                + ",\"valorUtilizado\":42.5"
                                + ",\"fotos\":[\"FOTOB64_1\",\"FOTOB64_2\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.doadorId").value(doador.getId()))
                .andExpect(jsonPath("$.doadorNome").value(doador.getNome()))
                .andExpect(jsonPath("$.ongNome").value(ong.getNome()))
                .andExpect(jsonPath("$.necessidadeTitulo").value("Cestas basicas"))
                .andExpect(jsonPath("$.valorUtilizado").value(42.5))
                .andExpect(jsonPath("$.fotos", hasSize(2)))
                .andExpect(jsonPath("$.fotos[0]").value("FOTOB64_1"));

        // o GET tambem devolve as fotos e o contexto
        mockMvc.perform(get("/prestacoes?interesseId=" + match.getId())
                        .header("Authorization", "Bearer " + token(doador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fotos", hasSize(2)))
                .andExpect(jsonPath("$[0].doadorNome").value(doador.getNome()));

        // notificacao ao doador com o texto do contrato
        boolean notificado = notificacaoRepository
                .findByUsuarioIdOrderByDataCriacaoDesc(doador.getId()).stream()
                .anyMatch(n -> n.getMensagem() != null
                        && n.getMensagem().contains("Prestação de contas sobre sua doação")
                        && n.getMensagem().contains("Veja o feedback!"));
        assertTrue(notificado, "doador deve ser notificado da prestacao");
    }

    @Test
    void prestacao_emMatchPendente_400() throws Exception {
        Ong ong = criarOng("ONG Prestacao Pendente");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorPrestPend");
        Interesse match = criarInteresse(
                criarNecessidade(ong, "Roupas"), doador, "PENDENTE");

        mockMvc.perform(post("/prestacoes")
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"interesseId\":" + match.getId()
                                + ",\"titulo\":\"x\",\"descricao\":\"y\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pendencias_listaConcluidosSemPrestacao_comPrazoEDefinitivo() throws Exception {
        Ong ong = criarOng("ONG Pendencias");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorPendencias");

        // pendencia recente (dentro do prazo). Usamos 2 dias MENOS 1 hora de
        // margem de proposito: com exatamente minusDays(2), a conclusao fica na
        // FRONTEIRA de 48h e o ChronoUnit.DAYS.between (que conta dias inteiros)
        // ora dava 2, ora 1 — dependendo dos milissegundos entre o now() do teste
        // e o now() do service e da truncacao do timestamp no H2 —, deixando
        // diasRestantes flaky entre 8 e 9. A margem de 1h garante 2 dias inteiros
        // decorridos de forma deterministica (restantes = 8) sem mudar a semantica.
        Interesse recente = criarInteresse(
                criarNecessidade(ong, "Pendencia Recente"), doador, "CONCLUIDO");
        recente.setDataConclusao(LocalDateTime.now().minusDays(2).minusHours(1));
        interesseRepository.save(recente);

        // pendencia estourada (definitiva)
        Interesse estourada = criarInteresse(
                criarNecessidade(ong, "Pendencia Estourada"), doador, "CONCLUIDO");
        estourada.setDataConclusao(LocalDateTime.now().minusDays(15));
        interesseRepository.save(estourada);

        mockMvc.perform(get("/prestacoes/pendencias?ongId=" + ong.getId())
                        .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.interesseId == " + recente.getId()
                        + ")].definitivo", hasItem(false)))
                .andExpect(jsonPath("$[?(@.interesseId == " + recente.getId()
                        + ")].diasRestantes", hasItem(8)))
                .andExpect(jsonPath("$[?(@.interesseId == " + estourada.getId()
                        + ")].definitivo", hasItem(true)))
                .andExpect(jsonPath("$[?(@.interesseId == " + estourada.getId()
                        + ")].diasRestantes", hasItem(0)))
                .andExpect(jsonPath("$[?(@.interesseId == " + estourada.getId()
                        + ")].doadorNome", hasItem(doador.getNome())));

        // por nao-dono -> 403
        Ong outra = criarOng("ONG Bisbilhoteira");
        Usuario contaOutra = criarContaOng(outra);
        mockMvc.perform(get("/prestacoes/pendencias?ongId=" + ong.getId())
                        .header("Authorization", "Bearer " + token(contaOutra)))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendenciaDefinitiva_derrubaScoreDeTransparencia() throws Exception {
        Ong ong = criarOng("ONG Score Penalizado");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorScore");

        // Da 5 pontos a ONG: 1 prestacao publicada num match aceito.
        Interesse comPrestacao = criarInteresse(
                criarNecessidade(ong, "Match com prestacao"), doador, "ACEITO");
        mockMvc.perform(post("/prestacoes")
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"interesseId\":" + comPrestacao.getId()
                                + ",\"titulo\":\"ok\",\"descricao\":\"ok\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/ongs/" + ong.getId() + "/transparencia")
                        .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(5));

        // Agora uma pendencia DEFINITIVA (concluida ha 15 dias, sem prestacao):
        // -5 pontos => score volta a 0 (piso).
        Interesse pendente = criarInteresse(
                criarNecessidade(ong, "Match esquecido"), doador, "CONCLUIDO");
        pendente.setDataConclusao(LocalDateTime.now().minusDays(15));
        interesseRepository.save(pendente);

        mockMvc.perform(get("/ongs/" + ong.getId() + "/transparencia")
                        .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0));
    }

    // ===================== D) Avaliacao de doador + perfil publico =====================

    @Test
    void avaliacaoDoador_upsertPorOngEDoador_recalculaMedia() throws Exception {
        Ong ong1 = criarOng("ONG Avaliadora 1");
        Usuario conta1 = criarContaOng(ong1);
        Ong ong2 = criarOng("ONG Avaliadora 2");
        Usuario conta2 = criarContaOng(ong2);
        Usuario doador = criarDoador("DoadorAvaliado");

        // LASTRO: a ONG so avalia um doador com quem concluiu uma doacao. Cada
        // ONG precisa de um match CONCLUIDO com este doador antes de avaliar.
        criarInteresse(criarNecessidade(ong1, "Doacao 1"), doador, "CONCLUIDO");
        criarInteresse(criarNecessidade(ong2, "Doacao 2"), doador, "CONCLUIDO");

        // ONG1 avalia com 4
        mockMvc.perform(post("/avaliacoes-doador")
                        .header("Authorization", "Bearer " + token(conta1))
                        .contentType("application/json")
                        .content("{\"doadorId\":" + doador.getId()
                                + ",\"nota\":4,\"comentario\":\"Pontual\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ongNome").value(ong1.getNome()))
                .andExpect(jsonPath("$.nota").value(4));

        Usuario aposPrimeira = usuarioRepository.findById(doador.getId()).orElseThrow();
        assertEquals(4.0, aposPrimeira.getNotaMediaDoador());
        assertEquals(1, aposPrimeira.getTotalAvaliacoesDoador());

        // ONG1 REavalia com 2 -> UPSERT (nao duplica) e recalcula
        mockMvc.perform(post("/avaliacoes-doador")
                        .header("Authorization", "Bearer " + token(conta1))
                        .contentType("application/json")
                        .content("{\"doadorId\":" + doador.getId() + ",\"nota\":2}"))
                .andExpect(status().isCreated());

        Usuario aposUpsert = usuarioRepository.findById(doador.getId()).orElseThrow();
        assertEquals(2.0, aposUpsert.getNotaMediaDoador());
        assertEquals(1, aposUpsert.getTotalAvaliacoesDoador());
        assertEquals(1, avaliacaoDoadorRepository
                .findByDoadorIdOrderByCriadoEmDesc(doador.getId()).size());

        // ONG2 avalia com 4 -> media (2+4)/2 = 3.0, total 2
        mockMvc.perform(post("/avaliacoes-doador")
                        .header("Authorization", "Bearer " + token(conta2))
                        .contentType("application/json")
                        .content("{\"doadorId\":" + doador.getId() + ",\"nota\":4}"))
                .andExpect(status().isCreated());

        Usuario aposSegundaOng = usuarioRepository.findById(doador.getId()).orElseThrow();
        assertEquals(3.0, aposSegundaOng.getNotaMediaDoador());
        assertEquals(2, aposSegundaOng.getTotalAvaliacoesDoador());

        // notificacao "A ONG <nome> avaliou voce com N estrelas"
        boolean notificado = notificacaoRepository
                .findByUsuarioIdOrderByDataCriacaoDesc(doador.getId()).stream()
                .anyMatch(n -> n.getMensagem() != null
                        && n.getMensagem().contains("avaliou você com 4 estrelas"));
        assertTrue(notificado);

        // GET publico (sem token) lista as avaliacoes
        mockMvc.perform(get("/avaliacoes-doador?doadorId=" + doador.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].ongNome",
                        hasItems(ong1.getNome(), ong2.getNome())));
    }

    @Test
    void avaliacaoDoador_porDoador_403() throws Exception {
        Usuario doador = criarDoador("DoadorNaoAvalia");
        Usuario alvo = criarDoador("DoadorAlvo");

        mockMvc.perform(post("/avaliacoes-doador")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"doadorId\":" + alvo.getId() + ",\"nota\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void avaliacaoDoador_semMatchConcluido_403() throws Exception {
        // A ONG nunca concluiu doacao com este doador -> nao pode avalia-lo
        // (avaliacao exige lastro; anti "review bombing").
        Ong ong = criarOng("ONG Sem Lastro Doador");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorSemMatchConcluido");

        mockMvc.perform(post("/avaliacoes-doador")
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"doadorId\":" + doador.getId() + ",\"nota\":5}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void avaliacaoOng_exigeMatchConcluido_403semLastro_201aposConcluir() throws Exception {
        Ong ong = criarOng("ONG Avaliada Pelo Doador");
        criarContaOng(ong);
        Usuario doador = criarDoador("DoadorQueAvaliaOng");

        // sem match concluido com a ONG -> 403
        mockMvc.perform(post("/avaliacoes")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"ongId\":" + ong.getId()
                                + ",\"doadorId\":" + doador.getId()
                                + ",\"nota\":5,\"comentario\":\"Otima\"}"))
                .andExpect(status().isForbidden());

        // apos concluir uma doacao com a ONG -> pode avaliar (201)
        criarInteresse(criarNecessidade(ong, "Cestas basicas"), doador, "CONCLUIDO");
        mockMvc.perform(post("/avaliacoes")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"ongId\":" + ong.getId()
                                + ",\"doadorId\":" + doador.getId()
                                + ",\"nota\":5,\"comentario\":\"Otima\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").value(5));
    }

    @Test
    void perfilPublicoDoador_ehPublico_eNaoVazaEmailNemValores() throws Exception {
        Ong ong = criarOng("ONG Perfil Doador");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorPerfilPublico");
        doador.setFotoBase64("FOTODEPERFILB64");
        usuarioRepository.save(doador);

        // 1 match concluido + 1 doacao PIX + 1 prestacao recebida + 1 avaliacao
        Necessidade nec = criarNecessidade(ong, "Agasalhos");
        Interesse match = criarInteresse(nec, doador, "CONCLUIDO");
        match.setDataConclusao(LocalDateTime.now().minusDays(1));
        interesseRepository.save(match);

        mockMvc.perform(post("/prestacoes")
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"interesseId\":" + match.getId()
                                + ",\"titulo\":\"Agasalhos entregues\""
                                + ",\"descricao\":\"obrigado!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/doacoes-financeiras")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"ongId\":" + ong.getId()
                                + ",\"doadorId\":" + doador.getId()
                                + ",\"valor\":77.0}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/avaliacoes-doador")
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"doadorId\":" + doador.getId()
                                + ",\"nota\":5,\"comentario\":\"Top\"}"))
                .andExpect(status().isCreated());

        // PUBLICO: sem Authorization
        mockMvc.perform(get("/usuarios/" + doador.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doador.getId()))
                .andExpect(jsonPath("$.nome").value(doador.getNome()))
                .andExpect(jsonPath("$.cidade").value("Campinas"))
                .andExpect(jsonPath("$.estado").value("SP"))
                .andExpect(jsonPath("$.fotoBase64").value("FOTODEPERFILB64"))
                .andExpect(jsonPath("$.membroDesde").isNotEmpty())
                .andExpect(jsonPath("$.notaMediaDoador").value(5.0))
                .andExpect(jsonPath("$.totalAvaliacoesDoador").value(1))
                .andExpect(jsonPath("$.stats.matchesConcluidos").value(1))
                .andExpect(jsonPath("$.stats.totalDoacoesPix").value(1))
                .andExpect(jsonPath("$.avaliacoes", hasSize(1)))
                .andExpect(jsonPath("$.avaliacoes[0].nota").value(5))
                .andExpect(jsonPath("$.prestacoesRecebidas", hasSize(1)))
                .andExpect(jsonPath("$.prestacoesRecebidas[0].titulo")
                        .value("Agasalhos entregues"))
                .andExpect(jsonPath("$.prestacoesRecebidas[0].ongNome").value(ong.getNome()))
                // contraparte da prestacao recebida: id (clique -> perfil da ONG)
                // + titulo da necessidade que gerou a doacao
                .andExpect(jsonPath("$.prestacoesRecebidas[0].ongId").value(ong.getId()))
                .andExpect(jsonPath("$.prestacoesRecebidas[0].necessidadeTitulo")
                        .value("Agasalhos"))
                // PRIVACIDADE: sem email/telefone e sem valores em R$
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.stats.valorTotalDoado").doesNotExist());
    }

    @Test
    void perfilPublicoDoador_404ParaOngESoftDeleted() throws Exception {
        // conta de ONG nao tem perfil publico de doador
        Ong ong = criarOng("ONG Sem Perfil Doador");
        Usuario contaOng = criarContaOng(ong);
        mockMvc.perform(get("/usuarios/" + contaOng.getId() + "/perfil-publico"))
                .andExpect(status().isNotFound());

        // doador soft-deleted some
        Usuario excluido = criarDoador("DoadorExcluido");
        excluido.setDataExclusao(LocalDateTime.now());
        usuarioRepository.save(excluido);
        mockMvc.perform(get("/usuarios/" + excluido.getId() + "/perfil-publico"))
                .andExpect(status().isNotFound());

        // inexistente
        mockMvc.perform(get("/usuarios/99999999/perfil-publico"))
                .andExpect(status().isNotFound());
    }

    // ===================== E) Fotos base64 no perfil da ONG =====================

    @Test
    void ong_putComCapaEnderecoEFotosLocal_eGetPorId() throws Exception {
        Ong ong = criarOng("ONG Perfil Rico");
        Usuario contaOng = criarContaOng(ong);

        mockMvc.perform(put("/ongs/" + ong.getId())
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"nome\":\"" + ong.getNome() + "\""
                                + ",\"email\":\"" + ong.getEmail() + "\""
                                + ",\"telefone\":\"1988887777\""
                                + ",\"cidade\":\"Campinas\""
                                + ",\"descricao\":\"nova desc\""
                                + ",\"capaBase64\":\"CAPAB64\""
                                + ",\"endereco\":\"Rua das Flores, 123\""
                                + ",\"fotosLocal\":[\"LOCAL1\",\"LOCAL2\",\"LOCAL3\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capaBase64").value("CAPAB64"))
                .andExpect(jsonPath("$.endereco").value("Rua das Flores, 123"))
                .andExpect(jsonPath("$.fotosLocal", hasSize(3)));

        // GET /ongs/{id} expoe capa/endereco/fotos
        mockMvc.perform(get("/ongs/" + ong.getId())
                        .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capaBase64").value("CAPAB64"))
                .andExpect(jsonPath("$.endereco").value("Rua das Flores, 123"))
                .andExpect(jsonPath("$.fotosLocal", hasSize(3)));

        // nova lista SUBSTITUI as fotos existentes
        mockMvc.perform(put("/ongs/" + ong.getId())
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"nome\":\"" + ong.getNome() + "\""
                                + ",\"email\":\"" + ong.getEmail() + "\""
                                + ",\"fotosLocal\":[\"NOVA_UNICA\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotosLocal", hasSize(1)))
                .andExpect(jsonPath("$.fotosLocal[0]").value("NOVA_UNICA"))
                // capa nao enviada = mantida
                .andExpect(jsonPath("$.capaBase64").value("CAPAB64"));

        // perfil publico tambem expoe
        mockMvc.perform(get("/ongs/" + ong.getId() + "/perfil-publico")
                        .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capaBase64").value("CAPAB64"))
                .andExpect(jsonPath("$.endereco").value("Rua das Flores, 123"))
                .andExpect(jsonPath("$.fotosLocal", hasSize(1)));

        // outra ONG nao edita (403)
        Ong intrusa = criarOng("ONG Intrusa Perfil");
        Usuario contaIntrusa = criarContaOng(intrusa);
        mockMvc.perform(put("/ongs/" + ong.getId())
                        .header("Authorization", "Bearer " + token(contaIntrusa))
                        .contentType("application/json")
                        .content("{\"nome\":\"hack\",\"email\":\"h@h.com\"}"))
                .andExpect(status().isForbidden());
    }

    // ===================== F) Chat: anexos + visto por ultimo =====================

    @Test
    void mensagem_anexoSemTexto_ok_eDevolvidaNoGet() throws Exception {
        Ong ong = criarOng("ONG Chat Anexo");
        criarContaOng(ong);
        Usuario doador = criarDoador("DoadorAnexo");
        Interesse match = criarInteresse(
                criarNecessidade(ong, "Chat com anexo"), doador, "ACEITO");

        mockMvc.perform(post("/mensagens")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"interesseId\":" + match.getId()
                                + ",\"remetente\":\"DOADOR\""
                                + ",\"anexoBase64\":\"IMGB64\""
                                + ",\"anexoTipo\":\"imagem\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.anexoBase64").value("IMGB64"))
                .andExpect(jsonPath("$.anexoTipo").value("imagem"));

        mockMvc.perform(get("/mensagens?interesseId=" + match.getId())
                        .header("Authorization", "Bearer " + token(doador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].anexoBase64").value("IMGB64"))
                .andExpect(jsonPath("$[0].anexoTipo").value("imagem"));

        // vazia (sem texto E sem anexo) continua proibida
        mockMvc.perform(post("/mensagens")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"interesseId\":" + match.getId()
                                + ",\"remetente\":\"DOADOR\",\"conteudo\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_continuaAberto_emMatchConcluido() throws Exception {
        Ong ong = criarOng("ONG Chat Concluido");
        criarContaOng(ong);
        Usuario doador = criarDoador("DoadorChatConcluido");
        Interesse match = criarInteresse(
                criarNecessidade(ong, "Chat pos-conclusao"), doador, "CONCLUIDO");
        match.setDataConclusao(LocalDateTime.now());
        interesseRepository.save(match);

        mockMvc.perform(post("/mensagens")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"interesseId\":" + match.getId()
                                + ",\"remetente\":\"DOADOR\""
                                + ",\"conteudo\":\"Obrigado pela confirmacao!\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void status_incluiUltimoVistoEpoch_eOnlineCalculadoNoServidor() throws Exception {
        Ong ong = criarOng("ONG Chat Status");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorStatus");
        Interesse match = criarInteresse(
                criarNecessidade(ong, "Status do chat"), doador, "ACEITO");

        // o DOADOR consulta o status -> registra o heartbeat DELE
        mockMvc.perform(get("/mensagens/status?interesseId=" + match.getId())
                        .header("Authorization", "Bearer " + token(doador)))
                .andExpect(status().isOk());

        // a ONG consulta: o outro lado (doador) acabou de dar sinal de vida
        long antes = System.currentTimeMillis();
        String corpo = mockMvc.perform(
                        get("/mensagens/status?interesseId=" + match.getId())
                                .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.online").value(true))
                .andExpect(jsonPath("$.ultimoVisto").isNotEmpty())
                .andExpect(jsonPath("$.ultimoVistoEpoch").isNumber())
                .andReturn().getResponse().getContentAsString();

        // o epoch e um instante REAL (UTC): precisa estar a segundos de "agora",
        // seja qual for o fuso do servidor (era o bug do 18:38 vs 11:38).
        long epoch = Long.parseLong(corpo.replaceAll(
                ".*\"ultimoVistoEpoch\":(\\d+).*", "$1"));
        assertTrue(Math.abs(antes - epoch) < 60_000,
                "ultimoVistoEpoch deve ser 'agora' em millis UTC (dif "
                        + Math.abs(antes - epoch) + "ms)");
    }

    // ===================== G) Streak do Top 1 do ranking =====================

    @Test
    void ranking_top1GanhaDiasNoTopo_eTrocaFechaOReinado() throws Exception {
        // Campea: verificada + nota 5 => score 50 (acima de qualquer ONG dos
        // outros testes, que no maximo somam prestacoes/campanhas ~25).
        Ong campea = criarOng("ONG Campea Streak");
        campea.setVerificada(true);
        campea.setNotaMedia(5.0);
        campea.setTotalAvaliacoes(10);
        ongRepository.save(campea);
        Usuario contaCampea = criarContaOng(campea);

        mockMvc.perform(get("/publico/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ongId").value(campea.getId()))
                .andExpect(jsonPath("$[0].diasNoTopo", greaterThanOrEqualTo(1)));

        // top1_desde aberto no banco
        Ong depois = ongRepository.findById(campea.getId()).orElseThrow();
        assertNotNull(depois.getTop1Desde());

        // perfil publico da atual #1 mostra diasNoTopo
        mockMvc.perform(get("/ongs/" + campea.getId() + "/perfil-publico")
                        .header("Authorization", "Bearer " + token(contaCampea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasNoTopo", greaterThanOrEqualTo(1)));

        // Uma rival passa na frente (verificada + nota 5 + 1 campanha encerrada = 55)
        Ong rival = criarOng("ONG Rival Streak");
        rival.setVerificada(true);
        rival.setNotaMedia(5.0);
        rival.setTotalAvaliacoes(10);
        ongRepository.save(rival);
        Campanha encerrada = criarCampanha(rival, "Campanha encerrada", 10.0);
        encerrada.setEncerrada(true);
        campanhaRepository.save(encerrada);

        mockMvc.perform(get("/publico/ranking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ongId").value(rival.getId()))
                .andExpect(jsonPath("$[0].diasNoTopo", greaterThanOrEqualTo(1)));

        // reinado da antiga campea foi fechado
        Ong destronada = ongRepository.findById(campea.getId()).orElseThrow();
        assertNull(destronada.getTop1Desde());
        assertNotNull(destronada.getUltimoReinadoDias());
        assertTrue(destronada.getUltimoReinadoDias() >= 1);

        // e o perfil publico dela passa a mostrar o ultimo reinado
        mockMvc.perform(get("/ongs/" + campea.getId() + "/perfil-publico")
                        .header("Authorization", "Bearer " + token(contaCampea)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasNoTopo").value(nullValue()))
                .andExpect(jsonPath("$.ultimoReinadoDias", greaterThanOrEqualTo(1)));
    }

    // ===================== H) Contraparte nas prestacoes da ONG =====================

    @Test
    void perfilPublicoOng_prestacoes_trazemDoadorIdNomeENecessidade() throws Exception {
        Ong ong = criarOng("ONG Prestacoes Contraparte");
        Usuario contaOng = criarContaOng(ong);
        Usuario doador = criarDoador("DoadorPrestacaoOng");
        Necessidade nec = criarNecessidade(ong, "Cestas basicas");
        Interesse match = criarInteresse(nec, doador, "CONCLUIDO");
        match.setDataConclusao(LocalDateTime.now().minusDays(1));
        interesseRepository.save(match);

        // a ONG presta contas dessa doacao
        mockMvc.perform(post("/prestacoes")
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"interesseId\":" + match.getId()
                                + ",\"titulo\":\"Cestas entregues\""
                                + ",\"descricao\":\"tudo certo\"}"))
                .andExpect(status().isCreated());

        // perfil publico da ONG: a prestacao carrega quem recebeu (dedupe + clique
        // -> perfil publico do doador) e o titulo da necessidade
        mockMvc.perform(get("/ongs/" + ong.getId() + "/perfil-publico")
                        .header("Authorization", "Bearer " + token(contaOng)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestacoes", hasSize(1)))
                .andExpect(jsonPath("$.prestacoes[0].titulo").value("Cestas entregues"))
                .andExpect(jsonPath("$.prestacoes[0].doadorId").value(doador.getId()))
                .andExpect(jsonPath("$.prestacoes[0].doadorNome").value(doador.getNome()))
                .andExpect(jsonPath("$.prestacoes[0].necessidadeTitulo")
                        .value("Cestas basicas"));
    }

    // ===================== I) Re-interesse apos conclusao =====================

    @Test
    void reInteresse_bloqueiaEmAndamento_masPermiteAposConcluidoOuRecusado()
            throws Exception {
        Ong ong = criarOng("ONG Re-interesse");
        criarContaOng(ong);

        // (1) interesse PENDENTE em andamento -> novo POST bloqueia
        Usuario doadorPend = criarDoador("DoadorRePendente");
        Necessidade necP = criarNecessidade(ong, "Doacao recorrente P");
        criarInteresse(necP, doadorPend, "PENDENTE");
        mockMvc.perform(post("/interesses")
                        .header("Authorization", "Bearer " + token(doadorPend))
                        .contentType("application/json")
                        .content("{\"necessidadeId\":" + necP.getId()
                                + ",\"doadorId\":" + doadorPend.getId() + "}"))
                .andExpect(status().isBadRequest());

        // (2) interesse ACEITO em andamento -> novo POST bloqueia
        Usuario doadorAceito = criarDoador("DoadorReAceito");
        Necessidade necA = criarNecessidade(ong, "Doacao recorrente A");
        criarInteresse(necA, doadorAceito, "ACEITO");
        mockMvc.perform(post("/interesses")
                        .header("Authorization", "Bearer " + token(doadorAceito))
                        .contentType("application/json")
                        .content("{\"necessidadeId\":" + necA.getId()
                                + ",\"doadorId\":" + doadorAceito.getId() + "}"))
                .andExpect(status().isBadRequest());

        // (3) unico interesse anterior CONCLUIDO -> novo POST cria (201)
        Usuario doadorConc = criarDoador("DoadorReConcluido");
        Necessidade necC = criarNecessidade(ong, "Doacao recorrente C");
        criarInteresse(necC, doadorConc, "CONCLUIDO");
        mockMvc.perform(post("/interesses")
                        .header("Authorization", "Bearer " + token(doadorConc))
                        .contentType("application/json")
                        .content("{\"necessidadeId\":" + necC.getId()
                                + ",\"doadorId\":" + doadorConc.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        // (4) unico interesse anterior RECUSADO -> novo POST cria (201)
        Usuario doadorRec = criarDoador("DoadorReRecusado");
        Necessidade necR = criarNecessidade(ong, "Doacao recorrente R");
        criarInteresse(necR, doadorRec, "RECUSADO");
        mockMvc.perform(post("/interesses")
                        .header("Authorization", "Bearer " + token(doadorRec))
                        .contentType("application/json")
                        .content("{\"necessidadeId\":" + necR.getId()
                                + ",\"doadorId\":" + doadorRec.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"));
    }
}
