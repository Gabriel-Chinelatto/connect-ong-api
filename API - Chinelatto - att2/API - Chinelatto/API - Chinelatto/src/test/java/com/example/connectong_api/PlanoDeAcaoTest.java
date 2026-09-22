package com.example.connectong_api;

import com.example.connectong_api.config.CriptografiaMigracao;
import com.example.connectong_api.model.*;
import com.example.connectong_api.repository.*;
import com.example.connectong_api.security.CampoCifradoConverter;
import com.example.connectong_api.security.Criptografia;
import com.example.connectong_api.service.JwtService;
import com.example.connectong_api.service.LgpdService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Os itens Must do Plano de Acao pos-FECITEC, provados via HTTP + banco (H2):
 * F-01 validacao de formato na API, F-03 criptografia em repouso,
 * F-04 LGPD (consentimento, exportacao, anonimizacao) e F-08 indicadores.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PlanoDeAcaoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Criptografia criptografia;
    @Autowired private CriptografiaMigracao migracao;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private MensagemRepository mensagemRepository;
    @Autowired private ConsentimentoRepository consentimentoRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    private Usuario doador(String nome, String telefone) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail("plano" + SEQ.getAndIncrement() + "@teste.local");
        u.setSenha(encoder.encode("doacao2026"));
        u.setTipo("DOADOR");
        u.setTelefone(telefone);
        return usuarioRepository.save(u);
    }

    private Ong ong(String nome) {
        return ongRepository.save(new Ong(nome, "plano-ong" + SEQ.getAndIncrement() + "@teste.local",
                "(19) 3451-2000", "Limeira - SP", "ONG de teste"));
    }

    private Usuario contaDaOng(Ong o) {
        Usuario u = new Usuario();
        u.setNome(o.getNome());
        u.setEmail("plano-conta" + SEQ.getAndIncrement() + "@teste.local");
        u.setSenha(encoder.encode("doacao2026"));
        u.setTipo("ONG");
        u.setOngId(o.getId());
        return usuarioRepository.save(u);
    }

    private Necessidade necessidade(Ong o, String categoria) {
        Necessidade n = new Necessidade();
        n.setOng(o);
        n.setTitulo("Cestas básicas");
        n.setDescricao("Para famílias do bairro");
        n.setCategoria(categoria);
        return necessidadeRepository.save(n);
    }

    private Interesse interesse(Necessidade n, Usuario d, String status, int diasResposta) {
        Interesse i = new Interesse();
        i.setNecessidade(n);
        i.setDoador(d);
        i.setStatus(status);
        i = interesseRepository.save(i);
        LocalDateTime criado = LocalDateTime.now().minusDays(20);
        i.setDataCriacao(criado);
        if ("CONCLUIDO".equals(status)) i.setDataConclusao(criado.plusDays(diasResposta));
        else if (!"PENDENTE".equals(status)) i.setDataStatus(criado.plusDays(diasResposta));
        return interesseRepository.save(i);
    }

    private String bearer(Usuario u) {
        return "Bearer " + jwtService.gerarAccessToken(u);
    }

    private String cadastro(String nome, String email, String telefone, Boolean aceite) {
        return "{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"doacao2026\""
                + (telefone == null ? "" : ",\"telefone\":\"" + telefone + "\"")
                + (aceite == null ? "" : ",\"aceiteTermos\":" + aceite) + "}";
    }

    // ===================== F-01: validacao de formato =====================

    @Test
    void f01_cadastroRecusaTelefoneNomeESenhaInvalidos() throws Exception {
        mockMvc.perform(post("/usuarios/registro").contentType("application/json")
                        .content(cadastro("Ana", "f01a@teste.local", "abc", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefone", containsString("Telefone inválido")));

        mockMvc.perform(post("/usuarios/registro").contentType("application/json")
                        .content(cadastro("!!!", "f01b@teste.local", null, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists());

        mockMvc.perform(post("/usuarios/registro").contentType("application/json")
                        .content("{\"nome\":\"Ana\",\"email\":\"f01c@teste.local\",\"senha\":\"123456\",\"aceiteTermos\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.senha", containsString("8 caracteres")));
    }

    @Test
    void f01_registroDeOngConfereDigitoDoCnpj() throws Exception {
        String base = "{\"nome\":\"Lar Teste\",\"senha\":\"doacao2026\",\"aceiteTermos\":true,\"cidade\":\"Limeira - SP\"";
        mockMvc.perform(post("/ongs/registro").contentType("application/json")
                        .content(base + ",\"email\":\"f01ong1@teste.local\",\"cnpj\":\"11.222.333/0001-82\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.cnpj", containsString("CNPJ inválido")));
        mockMvc.perform(post("/ongs/registro").contentType("application/json")
                        .content(base + ",\"email\":\"f01ong2@teste.local\",\"cnpj\":\"11.222.333/0001-81\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void f01_campanhaNaoTerminaAntesDeComecar() throws Exception {
        Ong o = ong("Lar Campanha");
        Usuario conta = contaDaOng(o);
        LocalDate hoje = LocalDate.now();
        mockMvc.perform(post("/campanhas").header("Authorization", bearer(conta)).contentType("application/json")
                        .content("{\"titulo\":\"Inverno solidário\",\"metaValor\":500,\"ongId\":" + o.getId()
                                + ",\"dataInicio\":\"" + hoje + "\",\"dataFim\":\"" + hoje.minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.dataFim", containsString("término")));
    }

    @Test
    void f01_perfilRecusaTelefoneEBioComHtml() throws Exception {
        Usuario u = doador("Bruno", null);
        mockMvc.perform(put("/usuarios/" + u.getId() + "/perfil").header("Authorization", bearer(u))
                        .contentType("application/json")
                        .content("{\"nome\":\"Bruno\",\"telefone\":\"12\",\"bio\":\"<b>oi</b>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefone").exists())
                .andExpect(jsonPath("$.campos.bio").exists());
    }

    // ===================== F-03: criptografia em repouso =====================

    @Test
    void f03_telefoneEMensagemFicamCifradosNoBanco_eLegiveisNaApi() {
        Usuario u = doador("Carla", "(19) 99876-5432");
        String bruto = jdbc.queryForObject("SELECT telefone FROM usuario WHERE id = ?", String.class, u.getId());
        assertTrue(bruto.startsWith("enc:v1:"), "no banco: " + bruto);
        assertFalse(bruto.contains("99876"));
        assertEquals("(19) 99876-5432", usuarioRepository.findById(u.getId()).orElseThrow().getTelefone());

        Necessidade n = necessidade(ong("Lar Chat"), "Alimentos");
        Interesse i = interesse(n, u, "ACEITO", 1);
        Mensagem m = new Mensagem();
        m.setInteresse(i);
        m.setRemetente("DOADOR");
        m.setConteudo("Posso levar amanhã às 10h");
        m = mensagemRepository.save(m);
        String brutoMsg = jdbc.queryForObject("SELECT conteudo FROM mensagem WHERE id = ?", String.class, m.getId());
        assertTrue(brutoMsg.startsWith("enc:v1:"));
        assertEquals("Posso levar amanhã às 10h", mensagemRepository.findById(m.getId()).orElseThrow().getConteudo());
    }

    @Test
    void f03_mesmoTextoGeraCifrasDiferentes_eAdulteracaoERecusada() {
        String a = criptografia.cifrar("(19) 99876-5432");
        String b = criptografia.cifrar("(19) 99876-5432");
        assertNotEquals(a, b, "IV aleatorio: mesma entrada, cifras diferentes");
        assertEquals("(19) 99876-5432", criptografia.decifrar(a));

        char[] c = a.toCharArray();
        int pos = c.length - 5;
        c[pos] = c[pos] == 'A' ? 'B' : 'A';
        String adulterado = new String(c);
        assertThrows(Criptografia.DadoAdulteradoException.class, () -> criptografia.decifrar(adulterado));
        assertEquals(CampoCifradoConverter.ILEGIVEL, new CampoCifradoConverter().convertToEntityAttribute(adulterado));
    }

    @Test
    void f03_migracaoCifraDadoLegadoEmTextoPuro() {
        Usuario u = doador("Legado", null);
        Usuario u2 = doador("Legado Dois", null);
        jdbc.update("UPDATE usuario SET telefone = ? WHERE id = ?", "(19) 3451-9999", u.getId());
        jdbc.update("UPDATE usuario SET telefone = ? WHERE id = ?", "(11) 98765-4321", u2.getId());
        assertTrue(migracao.migrar("usuario", "telefone") >= 2, "o lote cifra varias linhas num comando so");
        assertEquals("(11) 98765-4321", usuarioRepository.findById(u2.getId()).orElseThrow().getTelefone());
        String bruto = jdbc.queryForObject("SELECT telefone FROM usuario WHERE id = ?", String.class, u.getId());
        assertTrue(bruto.startsWith("enc:v1:"));
        assertEquals("(19) 3451-9999", usuarioRepository.findById(u.getId()).orElseThrow().getTelefone());
        assertEquals(0, migracao.migrar("usuario", "telefone"), "idempotente: nada mais a cifrar");
    }

    @Test
    void f03_ambienteComOutraChaveNaoSobe() {
        migracao.conferirChave(); // registra (ou confere) a chave da suite
        String original = jdbc.queryForObject("SELECT impressao_digital FROM cripto_chave WHERE id = 1", String.class);
        jdbc.update("UPDATE cripto_chave SET impressao_digital = ? WHERE id = 1", "0".repeat(64));
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> migracao.conferirChave());
            assertTrue(e.getMessage().contains("DIFERENTE"));
        } finally {
            jdbc.update("UPDATE cripto_chave SET impressao_digital = ? WHERE id = 1", original);
        }
    }

    @Test
    void f03_statusPublicoDeSeguranca() throws Exception {
        mockMvc.perform(get("/publico/seguranca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criptografiaEmRepouso").value(true))
                .andExpect(jsonPath("$.algoritmo").value("AES-256-GCM"));
    }

    // ===================== F-04: LGPD =====================

    @Test
    void f04_cadastroExigeAceite_eGravaConsentimento() throws Exception {
        mockMvc.perform(post("/usuarios/registro").contentType("application/json")
                        .content(cadastro("Dora", "f04a@teste.local", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.aceiteTermos", containsString("aceitar")));
        mockMvc.perform(post("/usuarios/registro").contentType("application/json")
                        .content(cadastro("Dora", "f04b@teste.local", null, false)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/usuarios/registro").contentType("application/json")
                        .content(cadastro("Dora", "f04c@teste.local", "(19) 99876-5432", true)))
                .andExpect(status().isCreated());
        Long id = usuarioRepository.findByEmail("f04c@teste.local").orElseThrow().getId();
        var aceites = consentimentoRepository.findByUsuarioIdOrderByAceitoEmDesc(id);
        assertEquals(1, aceites.size());
        assertEquals(Consentimento.VERSAO_ATUAL, aceites.get(0).getVersao());
    }

    @Test
    void f04_exportarMeusDados_soODono() throws Exception {
        Usuario u = doador("Eva", "(19) 99876-1111");
        Usuario outro = doador("Intruso", null);
        Interesse i = interesse(necessidade(ong("Lar Export"), "Roupas"), u, "ACEITO", 2);
        Mensagem m = new Mensagem();
        m.setInteresse(i);
        m.setRemetente("DOADOR");
        m.setConteudo("Separei 3 casacos");
        mensagemRepository.save(m);

        mockMvc.perform(get("/usuarios/" + u.getId() + "/meus-dados").header("Authorization", bearer(u)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(jsonPath("$.conta.telefone").value("(19) 99876-1111"))
                .andExpect(jsonPath("$.interessesEConversas[0].conversa[0].texto").value("Separei 3 casacos"))
                .andExpect(content().string(not(containsString("doacao2026"))))
                .andExpect(content().string(not(containsString("$2a$"))));

        mockMvc.perform(get("/usuarios/" + u.getId() + "/meus-dados").header("Authorization", bearer(outro)))
                .andExpect(status().isForbidden());
    }

    @Test
    void f04_excluirContaAnonimizaOsDadosPessoais() throws Exception {
        Usuario u = doador("Fabio Pessoal", "(19) 99876-2222");
        u.setCidade("Limeira");
        u.setBio("Gosto de ajudar");
        usuarioRepository.save(u);
        String emailOriginal = u.getEmail();

        mockMvc.perform(delete("/usuarios/" + u.getId()).header("Authorization", bearer(u)))
                .andExpect(status().isOk());

        Usuario depois = usuarioRepository.findById(u.getId()).orElseThrow();
        assertNotNull(depois.getDataExclusao());
        assertEquals(LgpdService.NOME_ANONIMO, depois.getNome());
        assertNotEquals(emailOriginal, depois.getEmail());
        assertTrue(depois.getEmail().endsWith("@anonimizado.invalid"));
        assertNull(depois.getTelefone());
        assertNull(depois.getCidade());
        assertNull(depois.getBio());
        assertTrue(usuarioRepository.findByEmail(emailOriginal).isEmpty(), "e-mail liberado");
    }

    // ===================== F-08: indicadores =====================

    @Test
    void f08_indicadoresDaOng_funilTaxasEMedianas() throws Exception {
        Ong o = ong("Lar Indicadores");
        Usuario conta = contaDaOng(o);
        Usuario d = doador("Gil", null);
        Necessidade n1 = necessidade(o, "Alimentos");
        Necessidade n2 = necessidade(o, "Alimentos");
        Necessidade n3 = necessidade(o, "Roupas");
        necessidade(o, "Roupas"); // sem nenhum interesse
        interesse(n1, d, "CONCLUIDO", 6);
        interesse(n1, d, "RECUSADO", 1);
        interesse(n2, d, "ACEITO", 3);
        interesse(n3, d, "PENDENTE", 0);

        mockMvc.perform(get("/ongs/" + o.getId() + "/indicadores").header("Authorization", bearer(conta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.funil.necessidades").value(4))
                .andExpect(jsonPath("$.funil.necessidadesComInteresse").value(3))
                .andExpect(jsonPath("$.funil.necessidadesAtendidas").value(1))
                .andExpect(jsonPath("$.funil.interesses").value(4))
                .andExpect(jsonPath("$.funil.interessesAceitos").value(2))
                .andExpect(jsonPath("$.funil.interessesConcluidos").value(1))
                .andExpect(jsonPath("$.taxaAtendimento").value(25.0))
                .andExpect(jsonPath("$.taxaEngajamento").value(75.0))
                .andExpect(jsonPath("$.taxaAceite").value(66.7))
                .andExpect(jsonPath("$.diasAteRespostaDaOng").value(2.0))
                .andExpect(jsonPath("$.diasAteConclusao").value(6.0))
                .andExpect(jsonPath("$.porCategoria", hasSize(2)))
                .andExpect(jsonPath("$.porEstado[0].uf").value("SP"))
                .andExpect(jsonPath("$.mensal", hasSize(12)));

        Usuario outraOng = contaDaOng(ong("Lar Curioso"));
        mockMvc.perform(get("/ongs/" + o.getId() + "/indicadores").header("Authorization", bearer(outraOng)))
                .andExpect(status().isForbidden());
    }

    @Test
    void f08_indicadoresPublicosSemLogin() throws Exception {
        mockMvc.perform(get("/publico/indicadores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escopo").value("PLATAFORMA"))
                .andExpect(jsonPath("$.funil.necessidades").isNumber())
                .andExpect(jsonPath("$.mensal", hasSize(12)));
    }
}
