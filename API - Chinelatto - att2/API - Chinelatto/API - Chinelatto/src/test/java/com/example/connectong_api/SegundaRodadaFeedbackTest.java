package com.example.connectong_api;

import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Notificacao;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.NotificacaoRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.PreferenciaRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.service.JwtService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 2a rodada de feedback (via HTTP MockMvc/H2):
 *
 *  1) PRIVACIDADE REAL do DOADOR: email/telefone no perfil publico so aparecem
 *     quando os toggles mostrarEmail/mostrarTelefone estao ligados.
 *  2) ALTERAR E-MAIL: senha errada -> 401, e-mail duplicado -> 409, ok -> 200;
 *     nao-dono -> 403.
 *  3) 2FA: login com doisFatores=1 nao emite tokens (requer2fa + codigoDemo);
 *     /auth/login-2fa com o codigo certo emite os tokens, codigo errado -> 400;
 *     doisFatores=0 mantem o login normal.
 *  4) EDITAR NECESSIDADE: nao-dono -> 403, dono -> 200 com os campos atualizados.
 *  5) NOTIFICACAO de novo interesse: a ONG dona recebe a notificacao.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SegundaRodadaFeedbackTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private BCryptPasswordEncoder encoder;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private PreferenciaRepository preferenciaRepository;
    @Autowired private NotificacaoRepository notificacaoRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    // ===================== helpers de seed =====================

    private Usuario criarDoador(String nome) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail("doador" + SEQ.getAndIncrement() + "@rodada2.test");
        u.setSenha(encoder.encode("123456"));
        u.setTipo("DOADOR");
        u.setCidade("Campinas");
        u.setEstado("SP");
        u.setTelefone("19999990000");
        return usuarioRepository.save(u);
    }

    private Ong criarOng(String nome) {
        Ong ong = new Ong(nome, "ong" + SEQ.getAndIncrement() + "@rodada2.test",
                "1999999999", "Campinas", "ONG de teste");
        return ongRepository.save(ong);
    }

    private Usuario criarContaOng(Ong ong) {
        Usuario u = new Usuario();
        u.setNome(ong.getNome());
        u.setEmail("conta" + SEQ.getAndIncrement() + "@rodada2.test");
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

    private Preferencia salvarPreferencia(Long usuarioId, Integer doisFatores,
                                          boolean mostrarEmail, boolean mostrarTelefone) {
        Preferencia p = Preferencia.padrao(usuarioId);
        p.setDoisFatores(doisFatores);
        p.setMostrarEmail(mostrarEmail);
        p.setMostrarTelefone(mostrarTelefone);
        return preferenciaRepository.save(p);
    }

    private String token(Usuario u) {
        return jwtService.gerarAccessToken(u);
    }

    // ===================== 1) PRIVACIDADE REAL do DOADOR =====================

    @Test
    void perfilPublicoDoador_togglesLigados_mostramEmailETelefone() throws Exception {
        Usuario doador = criarDoador("Doador Aberto");
        salvarPreferencia(doador.getId(), 0, true, true);

        mockMvc.perform(get("/usuarios/" + doador.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Doador Aberto"))
                .andExpect(jsonPath("$.email").value(doador.getEmail()))
                .andExpect(jsonPath("$.telefone").value("19999990000"));
    }

    @Test
    void perfilPublicoDoador_togglesDesligados_omitemEmailETelefone() throws Exception {
        Usuario doador = criarDoador("Doador Reservado");
        salvarPreferencia(doador.getId(), 0, false, false);

        mockMvc.perform(get("/usuarios/" + doador.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Doador Reservado"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").doesNotExist());
    }

    @Test
    void perfilPublicoDoador_togglesIndependentes_soTelefone() throws Exception {
        Usuario doador = criarDoador("Doador So Fone");
        salvarPreferencia(doador.getId(), 0, false, true);

        mockMvc.perform(get("/usuarios/" + doador.getId() + "/perfil-publico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.telefone").value("19999990000"));
    }

    // ===================== 2) ALTERAR E-MAIL =====================

    @Test
    void alterarEmail_senhaIncorreta_401() throws Exception {
        Usuario doador = criarDoador("Troca Email 1");

        mockMvc.perform(put("/usuarios/" + doador.getId() + "/email")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"novoEmail\": \"novo1@rodada2.test\", \"senha\": \"errada\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.erro").value("Senha incorreta."));
    }

    @Test
    void alterarEmail_duplicado_409() throws Exception {
        Usuario ocupante = criarDoador("Dono do Email");
        Usuario doador = criarDoador("Troca Email 2");

        mockMvc.perform(put("/usuarios/" + doador.getId() + "/email")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"novoEmail\": \"" + ocupante.getEmail()
                                + "\", \"senha\": \"123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("E-mail já cadastrado."));
    }

    @Test
    void alterarEmail_ok_200_ePersiste() throws Exception {
        Usuario doador = criarDoador("Troca Email 3");
        String novo = "trocado" + SEQ.getAndIncrement() + "@rodada2.test";

        mockMvc.perform(put("/usuarios/" + doador.getId() + "/email")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"novoEmail\": \"" + novo + "\", \"senha\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("E-mail alterado."))
                .andExpect(jsonPath("$.email").value(novo));

        assertEquals(novo, usuarioRepository.findById(doador.getId()).get().getEmail());
    }

    @Test
    void alterarEmail_naoDono_403() throws Exception {
        Usuario dono = criarDoador("Dono Real");
        Usuario intruso = criarDoador("Intruso");

        mockMvc.perform(put("/usuarios/" + dono.getId() + "/email")
                        .header("Authorization", "Bearer " + token(intruso))
                        .contentType("application/json")
                        .content("{\"novoEmail\": \"qualquer@rodada2.test\", \"senha\": \"123456\"}"))
                .andExpect(status().isForbidden());
    }

    // ===================== 3) 2FA =====================

    @Test
    void login_com2faLigado_naoEmiteTokens_retornaDesafioComCodigoDemo() throws Exception {
        Usuario doador = criarDoador("Com 2FA");
        salvarPreferencia(doador.getId(), 1, false, false);

        mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\": \"" + doador.getEmail() + "\", \"senha\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requer2fa").value(true))
                .andExpect(jsonPath("$.email").value(doador.getEmail()))
                .andExpect(jsonPath("$.codigoDemo").isNotEmpty())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void login2fa_codigoCerto_emiteTokens() throws Exception {
        Usuario doador = criarDoador("2FA Sucesso");
        salvarPreferencia(doador.getId(), 1, false, false);

        // passo 1: login devolve o codigoDemo
        MvcResult r = mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\": \"" + doador.getEmail() + "\", \"senha\": \"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode login = objectMapper.readTree(r.getResponse().getContentAsString());
        String codigo = login.get("codigoDemo").asText();

        // passo 2: login-2fa com o codigo certo -> tokens + campos do login normal
        mockMvc.perform(post("/auth/login-2fa")
                        .contentType("application/json")
                        .content("{\"email\": \"" + doador.getEmail()
                                + "\", \"codigo\": \"" + codigo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.id").value(doador.getId()))
                .andExpect(jsonPath("$.nome").value("2FA Sucesso"))
                .andExpect(jsonPath("$.tipo").value("DOADOR"));

        // o codigo e de uso unico: repetir agora falha
        mockMvc.perform(post("/auth/login-2fa")
                        .contentType("application/json")
                        .content("{\"email\": \"" + doador.getEmail()
                                + "\", \"codigo\": \"" + codigo + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Código inválido ou expirado."));
    }

    @Test
    void login2fa_codigoErrado_400() throws Exception {
        Usuario doador = criarDoador("2FA Erro");
        salvarPreferencia(doador.getId(), 1, false, false);

        mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\": \"" + doador.getEmail() + "\", \"senha\": \"123456\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login-2fa")
                        .contentType("application/json")
                        .content("{\"email\": \"" + doador.getEmail()
                                + "\", \"codigo\": \"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Código inválido ou expirado."));
    }

    @Test
    void login_doisFatoresDesligado_loginNormalComTokens() throws Exception {
        Usuario doador = criarDoador("Sem 2FA");
        salvarPreferencia(doador.getId(), 0, false, false);

        mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\": \"" + doador.getEmail() + "\", \"senha\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.requer2fa").doesNotExist());
    }

    @Test
    void login_semPreferencia_loginNormalComTokens() throws Exception {
        // Contas existentes (sem preferencia salva) NAO mudam de comportamento.
        Usuario doador = criarDoador("Legado");

        mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\": \"" + doador.getEmail() + "\", \"senha\": \"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.requer2fa").doesNotExist());
    }

    // ===================== 4) EDITAR NECESSIDADE =====================

    @Test
    void editarNecessidade_naoDono_403() throws Exception {
        Ong dona = criarOng("ONG Dona");
        criarContaOng(dona);
        Ong outra = criarOng("ONG Outra");
        Usuario contaOutra = criarContaOng(outra);
        Necessidade nec = criarNecessidade(dona, "Cestas basicas");

        mockMvc.perform(put("/necessidades/" + nec.getId())
                        .header("Authorization", "Bearer " + token(contaOutra))
                        .contentType("application/json")
                        .content("{\"titulo\": \"Hackeada\", \"descricao\": \"nao devia\","
                                + " \"categoria\": \"Roupas\", \"urgente\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarNecessidade_dono_200_atualizaCampos() throws Exception {
        Ong dona = criarOng("ONG Editora");
        Usuario conta = criarContaOng(dona);
        Necessidade nec = criarNecessidade(dona, "Titulo antigo");

        mockMvc.perform(put("/necessidades/" + nec.getId())
                        .header("Authorization", "Bearer " + token(conta))
                        .contentType("application/json")
                        .content("{\"titulo\": \"Titulo novo\", \"descricao\": \"desc nova\","
                                + " \"categoria\": \"Educação\", \"urgente\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Titulo novo"))
                .andExpect(jsonPath("$.descricao").value("desc nova"))
                // categoria normalizada como no POST (sem acento)
                .andExpect(jsonPath("$.categoria").value("Educacao"))
                .andExpect(jsonPath("$.urgente").value(true));

        Necessidade salva = necessidadeRepository.findById(nec.getId()).get();
        assertEquals("Titulo novo", salva.getTitulo());
        assertEquals("Educacao", salva.getCategoria());
    }

    @Test
    void editarNecessidade_inexistente_404() throws Exception {
        Ong dona = criarOng("ONG Vazia");
        Usuario conta = criarContaOng(dona);

        mockMvc.perform(put("/necessidades/99999999")
                        .header("Authorization", "Bearer " + token(conta))
                        .contentType("application/json")
                        .content("{\"titulo\": \"x titulo\", \"descricao\": \"y desc\","
                                + " \"categoria\": \"Saude\", \"urgente\": false}"))
                .andExpect(status().isNotFound());
    }

    // ===================== 5) NOTIFICACAO de novo interesse =====================

    @Test
    void novoInteresse_notificaOngDona() throws Exception {
        Ong ong = criarOng("ONG Notificada");
        Usuario contaOng = criarContaOng(ong);
        Necessidade nec = criarNecessidade(ong, "Material escolar");
        Usuario doador = criarDoador("Interessado");

        long antes = notificacaoRepository
                .findByUsuarioIdOrderByDataCriacaoDesc(contaOng.getId()).size();

        mockMvc.perform(post("/interesses")
                        .header("Authorization", "Bearer " + token(doador))
                        .contentType("application/json")
                        .content("{\"necessidadeId\": " + nec.getId()
                                + ", \"doadorId\": " + doador.getId() + "}"))
                .andExpect(status().isCreated());

        var notifs = notificacaoRepository
                .findByUsuarioIdOrderByDataCriacaoDesc(contaOng.getId());
        assertEquals(antes + 1, notifs.size(),
                "a ONG dona deve receber exatamente uma notificacao de novo interesse");
        Notificacao nova = notifs.get(0);
        assertEquals("MATCH", nova.getTipo());
        assertTrue(nova.getMensagem().contains("Material escolar"),
                "a notificacao cita a necessidade");
    }
}
