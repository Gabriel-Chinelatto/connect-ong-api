package com.example.connectong_api;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

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
}
