package com.example.connectong_api;

import com.example.connectong_api.model.Campanha;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.AuditLogRepository;
import com.example.connectong_api.repository.BloqueioRepository;
import com.example.connectong_api.repository.CampanhaRepository;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.PreferenciaRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.service.JwtService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contratos de bloqueio, privacidade e data de postagem, via HTTP (MockMvc/H2):
 *
 *  1) BLOQUEIO (ONG bloqueia doador): POST/DELETE/GET /bloqueios (so conta de
 *     ONG; idempotente; lista so da propria ONG) e o ENFORCEMENT — a ONG "some"
 *     do feed de necessidades/campanhas e da busca do doador bloqueado, o
 *     perfil publico vira corpo minimo {id, nome, bloqueado:true}, o chat trava
 *     nos dois sentidos (403; mensagem GENERICA para o doador) e o match ganha
 *     bloqueadoPelaOng=true. Desbloquear restaura tudo; doador nao bloqueado e
 *     requisicao anonima ficam inalterados.
 *
 *  2) PRIVACIDADE REAL: os toggles mostrarEmail/mostrarTelefone (Preferencia)
 *     passam a valer no GET /ongs/{id}/perfil-publico; o perfil publico do
 *     DOADOR continua sem email/telefone.
 *
 *  3) DATA DE POSTAGEM: dataCriacao presente no feed GET /necessidades e nas
 *     necessidades do perfil publico da ONG.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BloqueioPrivacidadeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder encoder;

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private BloqueioRepository bloqueioRepository;
    @Autowired private PreferenciaRepository preferenciaRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ===================== helpers de seed =====================

    private Usuario criarDoador(String nome) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail("doador" + SEQ.getAndIncrement() + "@bloqueio.test");
        u.setSenha(encoder.encode("123456"));
        u.setTipo("DOADOR");
        u.setCidade("Campinas");
        u.setEstado("SP");
        return usuarioRepository.save(u);
    }

    private Ong criarOng(String nome) {
        Ong ong = new Ong(nome, "ong" + SEQ.getAndIncrement() + "@bloqueio.test",
                "1999999999", "Campinas", "ONG de teste");
        return ongRepository.save(ong);
    }

    private Usuario criarContaOng(Ong ong) {
        Usuario u = new Usuario();
        u.setNome(ong.getNome());
        u.setEmail("conta" + SEQ.getAndIncrement() + "@bloqueio.test");
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

    private Campanha criarCampanha(Ong ong, String titulo) {
        Campanha c = new Campanha();
        c.setOng(ong);
        c.setTitulo(titulo);
        c.setDescricao("desc");
        c.setMetaValor(1000.0);
        c.setValorArrecadado(0.0);
        return campanhaRepository.save(c);
    }

    private Interesse criarInteresse(Necessidade nec, Usuario doador, String status) {
        Interesse i = new Interesse();
        i.setNecessidade(nec);
        i.setDoador(doador);
        i.setStatus(status);
        return interesseRepository.save(i);
    }

    private String token(Usuario u) {
        return jwtService.gerarAccessToken(u);
    }

    private void bloquear(Usuario contaOng, Usuario doador) throws Exception {
        mockMvc.perform(post("/bloqueios")
                        .header("Authorization", "Bearer " + token(contaOng))
                        .contentType("application/json")
                        .content("{\"doadorId\": " + doador.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Doador bloqueado."));
    }

    // ===================== 1) CRUD do bloqueio =====================

    @Test
    void bloquear_soContaDeOng_doadorRecebe403() throws Exception {
        Usuario doador = criarDoador("Sem Poder");
        Usuario alvo = criarDoador("Alvo");

        mockMvc.perform(post("/bloqueios")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"doadorId\": " + alvo.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bloquear_idempotente_naoDuplicaLinha_eRegistraAuditoria() throws Exception {
        Ong ong = criarOng("ONG Bloqueadora");
        Usuario conta = criarContaOng(ong);
        Usuario doador = criarDoador("Bloqueado Duplo");

        long auditAntes = auditLogRepository.findAll().stream()
                .filter(a -> "DOADOR_BLOQUEADO".equals(a.getAcao())).count();

        bloquear(conta, doador);
        bloquear(conta, doador); // repetir = 200 com a mesma mensagem

        assertEquals(1, bloqueioRepository.findByOngIdOrderByCriadoEmDesc(ong.getId()).size(),
                "bloquear duas vezes nao pode duplicar a linha");

        long auditDepois = auditLogRepository.findAll().stream()
                .filter(a -> "DOADOR_BLOQUEADO".equals(a.getAcao())).count();
        assertEquals(auditAntes + 1, auditDepois,
                "a auditoria DOADOR_BLOQUEADO e registrada uma unica vez");
    }

    @Test
    void bloquear_doadorInexistente_400() throws Exception {
        Ong ong = criarOng("ONG Sem Alvo");
        Usuario conta = criarContaOng(ong);

        mockMvc.perform(post("/bloqueios")
                        .header("Authorization", "Bearer " + token(conta))
                        .contentType("application/json")
                        .content("{\"doadorId\": 99999999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Doador não encontrado"));
    }

    @Test
    void listarBloqueios_soOsDaPropriaOng() throws Exception {
        Ong ongA = criarOng("ONG A");
        Usuario contaA = criarContaOng(ongA);
        Ong ongB = criarOng("ONG B");
        Usuario contaB = criarContaOng(ongB);
        Usuario doadorA = criarDoador("Doador da A");
        Usuario doadorB = criarDoador("Doador da B");

        bloquear(contaA, doadorA);
        bloquear(contaB, doadorB);

        mockMvc.perform(get("/bloqueios")
                        .header("Authorization", "Bearer " + token(contaA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].doadorId").value(doadorA.getId()))
                .andExpect(jsonPath("$[0].doadorNome").value("Doador da A"))
                .andExpect(jsonPath("$[0].criadoEm").isNotEmpty());
    }

    // ============ 1) ENFORCEMENT: feed, busca, perfil, chat, matches ============

    @Test
    void feedNecessidades_ongSomeParaBloqueado_eVoltaAposDesbloqueio() throws Exception {
        Ong ong = criarOng("ONG Invisivel");
        Usuario conta = criarContaOng(ong);
        criarNecessidade(ong, "Cestas basicas urgentes");
        Usuario bloqueado = criarDoador("Doador Bloqueado");
        Usuario livre = criarDoador("Doador Livre");

        // antes do bloqueio: o doador ve a necessidade
        mockMvc.perform(get("/necessidades")
                        .header("Authorization", "Bearer " + token(bloqueado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ongId == " + ong.getId() + ")]",
                        hasSize(greaterThan(0))));

        bloquear(conta, bloqueado);

        // depois: a ONG some do feed DO BLOQUEADO...
        mockMvc.perform(get("/necessidades")
                        .header("Authorization", "Bearer " + token(bloqueado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ongId == " + ong.getId() + ")]", hasSize(0)));

        // ...mas o doador NAO bloqueado continua vendo (inalterado)
        mockMvc.perform(get("/necessidades")
                        .header("Authorization", "Bearer " + token(livre)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ongId == " + ong.getId() + ")]",
                        hasSize(greaterThan(0))));

        // desbloquear restaura o feed
        mockMvc.perform(delete("/bloqueios/" + bloqueado.getId())
                        .header("Authorization", "Bearer " + token(conta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Doador desbloqueado."));

        mockMvc.perform(get("/necessidades")
                        .header("Authorization", "Bearer " + token(bloqueado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ongId == " + ong.getId() + ")]",
                        hasSize(greaterThan(0))));
    }

    @Test
    void feedCampanhas_ongSomeParaBloqueado() throws Exception {
        Ong ong = criarOng("ONG Campanheira");
        Usuario conta = criarContaOng(ong);
        criarCampanha(ong, "Campanha do agasalho");
        Usuario bloqueado = criarDoador("Doador Sem Campanha");

        bloquear(conta, bloqueado);

        mockMvc.perform(get("/campanhas")
                        .header("Authorization", "Bearer " + token(bloqueado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.ongId == " + ong.getId() + ")]", hasSize(0)));
    }

    @Test
    void buscaOngs_ongBloqueadoraSomeParaBloqueado() throws Exception {
        Ong ong = criarOng("ONG Escondida Da Busca");
        Usuario conta = criarContaOng(ong);
        Usuario bloqueado = criarDoador("Doador Buscador");

        bloquear(conta, bloqueado);

        mockMvc.perform(get("/ongs").param("nome", "Escondida Da Busca")
                        .header("Authorization", "Bearer " + token(bloqueado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // conta de ONG (nao doador) segue vendo normalmente
        mockMvc.perform(get("/ongs").param("nome", "Escondida Da Busca")
                        .header("Authorization", "Bearer " + token(conta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void perfilPublico_doadorBloqueadoRecebeCorpoMinimo_anonimoSegueNormal() throws Exception {
        Ong ong = criarOng("ONG Perfil Minimo");
        Usuario conta = criarContaOng(ong);
        criarNecessidade(ong, "Roupas de inverno");
        Usuario bloqueado = criarDoador("Doador Sem Perfil");

        bloquear(conta, bloqueado);

        // doador bloqueado: 200 com corpo MINIMO (sem o resto do perfil)
        mockMvc.perform(get("/ongs/" + ong.getId() + "/perfil-publico")
                        .header("Authorization", "Bearer " + token(bloqueado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ong.getId()))
                .andExpect(jsonPath("$.nome").value("ONG Perfil Minimo"))
                .andExpect(jsonPath("$.bloqueado").value(true))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.necessidades").doesNotExist());

        // anonimo (link compartilhavel): perfil completo, sem campo bloqueado
        mockMvc.perform(get("/ongs/" + ong.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueado").doesNotExist())
                .andExpect(jsonPath("$.necessidades", hasSize(1)));
    }

    @Test
    void chat_bloqueioTravaNosDoisSentidos_comMensagemGenericaProDoador() throws Exception {
        Ong ong = criarOng("ONG Muda");
        Usuario conta = criarContaOng(ong);
        Usuario doador = criarDoador("Doador Calado");
        Necessidade nec = criarNecessidade(ong, "Livros didaticos");
        Interesse match = criarInteresse(nec, doador, "ACEITO");

        // antes do bloqueio o chat funciona
        mockMvc.perform(post("/mensagens")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"interesseId\": " + match.getId()
                                + ", \"conteudo\": \"Ola!\"}"))
                .andExpect(status().isCreated());

        bloquear(conta, doador);

        // doador -> 403 com mensagem GENERICA (nao revela o bloqueio)
        mockMvc.perform(post("/mensagens")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"interesseId\": " + match.getId()
                                + ", \"conteudo\": \"Alguem ai?\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro")
                        .value("Não é possível enviar mensagens para esta ONG."));

        // a ONG bloqueadora tambem nao envia (mensagem propria)
        mockMvc.perform(post("/mensagens")
                        .header("Authorization", "Bearer " + token(conta))
                        .contentType("application/json")
                        .content("{\"interesseId\": " + match.getId()
                                + ", \"conteudo\": \"...\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro", containsString("bloqueou")));

        // desbloquear reabre o chat
        mockMvc.perform(delete("/bloqueios/" + doador.getId())
                        .header("Authorization", "Bearer " + token(conta)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/mensagens")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"interesseId\": " + match.getId()
                                + ", \"conteudo\": \"Voltei!\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void matches_continuamListados_masGanhamBloqueadoPelaOng() throws Exception {
        Ong ongBloqueadora = criarOng("ONG Que Bloqueia");
        Usuario contaBloqueadora = criarContaOng(ongBloqueadora);
        Ong ongNormal = criarOng("ONG Normal");
        Usuario doador = criarDoador("Doador Com Historico");

        Interesse matchBloqueado = criarInteresse(
                criarNecessidade(ongBloqueadora, "Fraldas"), doador, "ACEITO");
        Interesse matchNormal = criarInteresse(
                criarNecessidade(ongNormal, "Brinquedos"), doador, "ACEITO");

        bloquear(contaBloqueadora, doador);

        // o historico continua com os DOIS matches; so o da bloqueadora ganha a flag
        mockMvc.perform(get("/interesses").param("doadorId", doador.getId().toString())
                        .header("Authorization", "Bearer " + token(doador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == " + matchBloqueado.getId()
                        + ")].bloqueadoPelaOng").value(contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + matchNormal.getId()
                        + ")].bloqueadoPelaOng").value(contains(false)));

        // no lado da ONG bloqueadora a flag tambem aparece (desabilita o chat la)
        mockMvc.perform(get("/interesses")
                        .param("ongId", ongBloqueadora.getId().toString())
                        .header("Authorization", "Bearer " + token(contaBloqueadora)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bloqueadoPelaOng").value(true));
    }

    // ===================== 2) PRIVACIDADE REAL =====================

    @Test
    void perfilPublicoOng_togglesLigados_mostramEmailETelefone() throws Exception {
        Ong ong = criarOng("ONG Transparente");
        Usuario conta = criarContaOng(ong);

        // liga os DOIS toggles via PUT /usuarios/{id}/preferencias (contrato do Bloco 8)
        mockMvc.perform(put("/usuarios/" + conta.getId() + "/preferencias")
                        .header("Authorization", "Bearer " + token(conta))
                        .contentType("application/json")
                        .content("{\"mostrarEmail\": true, \"mostrarTelefone\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mostrarEmail").value(true))
                .andExpect(jsonPath("$.mostrarTelefone").value(true));

        mockMvc.perform(get("/ongs/" + ong.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ong.getEmail()))
                .andExpect(jsonPath("$.telefone").value("1999999999"));
    }

    @Test
    void perfilPublicoOng_togglesDesligados_omitemEmailETelefone() throws Exception {
        Ong ong = criarOng("ONG Reservada");
        Usuario conta = criarContaOng(ong);

        mockMvc.perform(put("/usuarios/" + conta.getId() + "/preferencias")
                        .header("Authorization", "Bearer " + token(conta))
                        .contentType("application/json")
                        .content("{\"mostrarEmail\": false, \"mostrarTelefone\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/ongs/" + ong.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.telefone").value(nullValue()))
                // o resto do perfil continua inteiro
                .andExpect(jsonPath("$.nome").value("ONG Reservada"));
    }

    @Test
    void perfilPublicoOng_semPreferenciaSalva_valemOsDefaults() throws Exception {
        // Defaults de Preferencia.padrao(): email OCULTO, telefone VISIVEL —
        // exatamente o que a tela de configuracoes mostra a quem nunca mexeu nela.
        Ong ong = criarOng("ONG Sem Preferencias");
        Usuario conta = criarContaOng(ong);
        assertTrue(preferenciaRepository.findByUsuarioId(conta.getId()).isEmpty());

        mockMvc.perform(get("/ongs/" + ong.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(nullValue()))
                .andExpect(jsonPath("$.telefone").value("1999999999"));
    }

    @Test
    void perfilPublicoDoador_nuncaExpoeEmailNemTelefone() throws Exception {
        Usuario doador = criarDoador("Doador Privado");
        doador.setTelefone("19988887777");
        usuarioRepository.save(doador);

        mockMvc.perform(get("/usuarios/" + doador.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Doador Privado"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist());
    }

    // ===================== 3) DATA DE POSTAGEM =====================

    @Test
    void necessidade_dataCriacao_presenteNoFeedENoPerfilPublico() throws Exception {
        Ong ong = criarOng("ONG Datada");
        Usuario doador = criarDoador("Doador Do Feed");
        Necessidade nec = criarNecessidade(ong, "Material escolar");
        assertNotNull(nec.getDataCriacao(), "@PrePersist deve preencher dataCriacao");

        // feed
        mockMvc.perform(get("/necessidades")
                        .param("ongId", ong.getId().toString())
                        .header("Authorization", "Bearer " + token(doador)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dataCriacao").isNotEmpty());

        // perfil publico da ONG
        mockMvc.perform(get("/ongs/" + ong.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.necessidades[0].dataCriacao").isNotEmpty());
    }
}
