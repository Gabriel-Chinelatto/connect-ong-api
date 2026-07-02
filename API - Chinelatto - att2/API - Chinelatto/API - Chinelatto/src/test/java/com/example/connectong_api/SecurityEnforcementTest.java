package com.example.connectong_api;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova que a camada de seguranca (SecurityFilterChain + JwtAuthFilter) esta
 * ATIVA e correta. Antes destas mudancas, todos os endpoints respondiam sem
 * autenticacao; estes testes falhariam.
 *
 * Roda contra o H2 em memoria (perfil de teste), com app.security.enforce no
 * default (true).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityEnforcementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    // Gera um access token valido para um doador ficticio (nao precisa de banco).
    private String tokenDoador() {
        Usuario u = new Usuario();
        u.setId(999L);
        u.setNome("Doador Teste");
        u.setTipo("DOADOR");
        return jwtService.gerarAccessToken(u);
    }

    // Token de um usuario ONG ficticio (tipo ONG + ongId) -> ROLE_ONG.
    private String tokenOng() {
        Usuario u = new Usuario();
        u.setId(500L);
        u.setNome("ONG Teste");
        u.setTipo("ONG");
        u.setOngId(42L);
        return jwtService.gerarAccessToken(u);
    }

    // Token de um administrador ficticio (tipo ADMIN) -> ROLE_ADMIN.
    private String tokenAdmin() {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNome("Admin Teste");
        u.setTipo("ADMIN");
        return jwtService.gerarAccessToken(u);
    }

    @Test
    void endpointProtegido_semToken_retorna401() throws Exception {
        // /atividades exige autenticacao -> sem token deve ser barrado
        mockMvc.perform(get("/atividades"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegido_comTokenValido_naoEhBarrado() throws Exception {
        // Com Bearer valido, a seguranca deixa passar (200 OK na listagem)
        mockMvc.perform(get("/atividades")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isOk());
    }

    @Test
    void endpointProtegido_comTokenInvalido_retorna401() throws Exception {
        mockMvc.perform(get("/atividades")
                        .header("Authorization", "Bearer token.invalido.aqui"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointPublico_publicoRanking_liberadoSemToken() throws Exception {
        // /publico/** e publico (e exercita o ranking otimizado com GROUP BY)
        mockMvc.perform(get("/publico/ranking"))
                .andExpect(status().isOk());
    }

    @Test
    void login_ehPublico_e_credenciaisInvalidasRetorna401() throws Exception {
        // O endpoint de login e acessivel sem token; credenciais erradas -> 401
        mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\":\"naoexiste@x.com\",\"senha\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ===================== Ownership (autorizacao) =====================

    @Test
    void ownership_notificacoesDeOutroUsuario_retorna403() throws Exception {
        // Token do usuario 999 tentando ler as notificacoes do usuario 1000
        mockMvc.perform(get("/notificacoes?usuarioId=1000")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownership_notificacoesProprias_retorna200() throws Exception {
        // Token do usuario 999 lendo as PROPRIAS notificacoes (999) -> liberado
        mockMvc.perform(get("/notificacoes?usuarioId=999")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isOk());
    }

    @Test
    void ownership_perfilDeOutroUsuario_retorna403() throws Exception {
        // Token do usuario 999 tentando ver o perfil do usuario 1000
        mockMvc.perform(get("/usuarios/1000/perfil")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownership_verificarOngDeOutro_retorna403() throws Exception {
        // Conceder o selo de verificacao a uma ONG (id 1) por quem NAO e dono da
        // ONG (o doador nao tem ongId no token) deve ser barrado com 403 — prova
        // que a IDOR "qualquer logado verifica qualquer ONG" foi fechada.
        mockMvc.perform(put("/ongs/1/verificar")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isForbidden());
    }

    @Test
    void audit_logs_comPapelDoador_retorna403() throws Exception {
        // /audit-logs e restrito a ROLE_ONG; um doador autenticado leva 403.
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isForbidden());
    }

    // ===================== Regressoes desta rodada =====================

    @Test
    void cadastroPublico_ignoraTipoOng_forcaDoador() throws Exception {
        // Escalonamento de privilegio: POST /usuarios e publico. Mesmo pedindo
        // tipo=ONG, o servidor DEVE gravar DOADOR (senao o cliente ganharia um
        // JWT com ROLE_ONG sem passar por /ongs/registro).
        mockMvc.perform(post("/usuarios")
                        .contentType("application/json")
                        .content("{\"nome\":\"Hacker\",\"email\":\"esc@x.com\","
                                + "\"senha\":\"senha123\",\"tipo\":\"ONG\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DOADOR"));
    }

    @Test
    void listarInteresses_semFiltro_retorna400() throws Exception {
        // IDOR: sem doadorId/ongId o service caia em findAll() e devolvia TODOS os
        // matches da plataforma. Agora exige um filtro -> 400 (e nunca findAll).
        mockMvc.perform(get("/interesses")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isBadRequest());
    }

    // ===================== ROLE_ADMIN (moderacao/auditoria) =====================
    // Antes estes endpoints eram ROLE_ONG; como ONG e auto-registravel, qualquer um
    // ganhava acesso administrativo. Agora exigem ROLE_ADMIN (nao auto-provisionavel).

    @Test
    void auditLogs_comPapelOng_retorna403() throws Exception {
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + tokenOng()))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLogs_comPapelAdmin_retorna200() throws Exception {
        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void listarDenuncias_comPapelOng_retorna403() throws Exception {
        mockMvc.perform(get("/denuncias")
                        .header("Authorization", "Bearer " + tokenOng()))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarDenuncias_comPapelAdmin_retorna200() throws Exception {
        mockMvc.perform(get("/denuncias")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isOk());
    }

    @Test
    void verificarOng_comPapelOng_retorna403() throws Exception {
        // Conceder o selo agora e so do admin: nem a propria ONG se auto-verifica.
        mockMvc.perform(put("/ongs/42/verificar")
                        .header("Authorization", "Bearer " + tokenOng()))
                .andExpect(status().isForbidden());
    }

    @Test
    void verificarOng_comPapelAdmin_passaPelaSeguranca() throws Exception {
        // Admin nao e barrado pela seguranca; como a ONG 12345 nao existe no H2,
        // o resultado e 404 (e nao 401/403), provando que o acesso foi liberado.
        mockMvc.perform(put("/ongs/12345/verificar")
                        .header("Authorization", "Bearer " + tokenAdmin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminBootstrap_loginComCredenciaisConfiguradas_retornaTipoAdmin() throws Exception {
        // Prova a cadeia completa: o AdminBootstrap criou a conta ADMIN (das
        // credenciais de teste) e o login a autentica devolvendo tipo=ADMIN.
        mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\":\"admin@teste.local\","
                                + "\"senha\":\"admin-teste-123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ADMIN"));
    }

    // ===================== Chat: presenca (status/digitando) =====================
    // Os novos endpoints de presenca ficam sob autenticacao (a posse do match e
    // validada no service). Sem token -> 401.

    @Test
    void chatStatus_semToken_retorna401() throws Exception {
        mockMvc.perform(get("/mensagens/status?interesseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatDigitando_semToken_retorna401() throws Exception {
        mockMvc.perform(post("/mensagens/digitando?interesseId=1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reagirMensagem_semToken_retorna401() throws Exception {
        mockMvc.perform(post("/mensagens/1/reacao")
                        .contentType("application/json")
                        .content("{\"emoji\":\"LIKE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void excluirConta_semToken_retorna401() throws Exception {
        mockMvc.perform(delete("/usuarios/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void excluirContaDeOutro_retorna403() throws Exception {
        // O usuario 999 (token) tentando excluir a conta do 1000 -> ownership 403.
        mockMvc.perform(delete("/usuarios/1000")
                        .header("Authorization", "Bearer " + tokenDoador()))
                .andExpect(status().isForbidden());
    }
}
