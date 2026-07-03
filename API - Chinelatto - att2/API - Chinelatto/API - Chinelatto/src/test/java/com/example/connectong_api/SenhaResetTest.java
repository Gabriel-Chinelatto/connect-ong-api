package com.example.connectong_api;

import com.example.connectong_api.model.SenhaReset;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.SenhaResetRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes do fluxo "esqueci a senha" (POST /auth/esqueci-senha e
 * /auth/redefinir-senha), ambos publicos (whitelist /auth/**): todas as
 * chamadas aqui sao feitas SEM token.
 *
 * Contrato fixo com os frontends:
 * - esqueci-senha SEMPRE responde 200 generico (anti-enumeracao), e com
 *   app.demo.enabled=true (default deste contexto) inclui codigoDemo
 *   (simulacao de e-mail da feira) SOMENTE quando o codigo foi gerado.
 * - redefinir-senha: sucesso 200; qualquer falha -> 400 generico.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SenhaResetTest {

    private static final String MENSAGEM_GENERICA =
            "Se o e-mail existir, enviaremos um código de recuperação.";

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private SenhaResetRepository senhaResetRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    private final ObjectMapper mapper = new ObjectMapper();

    private Usuario criarUsuario(String email, String senha) {
        Usuario u = new Usuario();
        u.setNome("Usuario Reset");
        u.setEmail(email);
        u.setTipo("DOADOR");
        u.setSenha(passwordEncoder.encode(senha));
        return usuarioRepository.save(u);
    }

    private MvcResult solicitar(String email) throws Exception {
        return mockMvc.perform(post("/auth/esqueci-senha")
                        .contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM_GENERICA))
                .andReturn();
    }

    private String codigoDemoDe(MvcResult res) throws Exception {
        JsonNode json = mapper.readTree(res.getResponse().getContentAsString());
        return json.has("codigoDemo") ? json.get("codigoDemo").asText() : null;
    }

    private org.springframework.test.web.servlet.ResultActions redefinir(
            String email, String codigo, String novaSenha) throws Exception {
        return mockMvc.perform(post("/auth/redefinir-senha")
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"codigo\":\"" + codigo
                        + "\",\"novaSenha\":\"" + novaSenha + "\"}"));
    }

    @Test
    void emailExistente_comDemoLigado_retornaCodigoDemoDe6Digitos() throws Exception {
        Usuario u = criarUsuario("reset.demo@teste.com", "senha-original");

        String codigo = codigoDemoDe(solicitar("reset.demo@teste.com"));

        assertNotNull(codigo, "com demo ligado e email existente, codigoDemo deve vir na resposta");
        assertTrue(codigo.matches("\\d{6}"), "codigo deve ter 6 digitos numericos");

        // o codigo foi persistido para o usuario certo, valido e nao usado
        List<SenhaReset> pendentes = senhaResetRepository.findByUsuarioIdAndUsadoEmIsNull(u.getId());
        assertEquals(1, pendentes.size());
        assertEquals(codigo, pendentes.get(0).getCodigo());
        assertTrue(pendentes.get(0).getExpiraEm().isAfter(LocalDateTime.now()));
    }

    @Test
    void emailInexistente_retorna200GenericoSemCodigoDemo() throws Exception {
        long antes = senhaResetRepository.count();

        MvcResult res = solicitar("nao.existe." + System.nanoTime() + "@teste.com");

        // mesma resposta generica, SEM codigoDemo e SEM gerar codigo (anti-enumeracao)
        assertNull(codigoDemoDe(res));
        assertEquals(antes, senhaResetRepository.count(), "nenhum codigo pode ser gerado");
    }

    @Test
    void contaSoftDeleted_retorna200GenericoSemGerarCodigo() throws Exception {
        Usuario u = criarUsuario("reset.excluido@teste.com", "senha-original");
        u.setDataExclusao(LocalDateTime.now());
        usuarioRepository.save(u);

        MvcResult res = solicitar("reset.excluido@teste.com");

        assertNull(codigoDemoDe(res), "conta excluida nao pode receber codigoDemo");
        assertTrue(senhaResetRepository.findByUsuarioIdAndUsadoEmIsNull(u.getId()).isEmpty(),
                "conta excluida nao pode gerar codigo");
    }

    @Test
    void fluxoCompleto_redefineSenha_eLoginFuncionaComANova() throws Exception {
        criarUsuario("reset.fluxo@teste.com", "senha-antiga");

        String codigo = codigoDemoDe(solicitar("reset.fluxo@teste.com"));

        redefinir("reset.fluxo@teste.com", codigo, "senha-nova-123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagem").value("Senha redefinida com sucesso."));

        // login com a senha ANTIGA deixa de funcionar...
        mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\":\"reset.fluxo@teste.com\",\"senha\":\"senha-antiga\"}"))
                .andExpect(status().isUnauthorized());

        // ...e com a senha NOVA funciona
        mockMvc.perform(post("/usuarios/login")
                        .contentType("application/json")
                        .content("{\"email\":\"reset.fluxo@teste.com\",\"senha\":\"senha-nova-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void codigoErrado_retorna400Generico() throws Exception {
        criarUsuario("reset.errado@teste.com", "senha-original");
        solicitar("reset.errado@teste.com");

        redefinir("reset.errado@teste.com", "000000x", "senha-nova-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Código inválido ou expirado."));
    }

    @Test
    void codigoExpirado_retorna400Generico() throws Exception {
        Usuario u = criarUsuario("reset.expirado@teste.com", "senha-original");
        String codigo = codigoDemoDe(solicitar("reset.expirado@teste.com"));

        // forca a expiracao do codigo direto no banco
        SenhaReset r = senhaResetRepository.findByUsuarioIdAndUsadoEmIsNull(u.getId()).get(0);
        r.setExpiraEm(LocalDateTime.now().minusMinutes(1));
        senhaResetRepository.save(r);

        redefinir("reset.expirado@teste.com", codigo, "senha-nova-123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Código inválido ou expirado."));
    }

    @Test
    void codigoJaUsado_naoPodeSerReutilizado() throws Exception {
        criarUsuario("reset.reuso@teste.com", "senha-original");
        String codigo = codigoDemoDe(solicitar("reset.reuso@teste.com"));

        redefinir("reset.reuso@teste.com", codigo, "senha-nova-123")
                .andExpect(status().isOk());

        // reutilizar o MESMO codigo -> 400 (usadoEm ja preenchido)
        redefinir("reset.reuso@teste.com", codigo, "senha-hacker-999")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("Código inválido ou expirado."));
    }

    @Test
    void novoCodigo_invalidaOAnterior() throws Exception {
        criarUsuario("reset.invalida@teste.com", "senha-original");

        String primeiro = codigoDemoDe(solicitar("reset.invalida@teste.com"));
        String segundo = codigoDemoDe(solicitar("reset.invalida@teste.com"));

        // o codigo antigo foi invalidado ao gerar o novo
        redefinir("reset.invalida@teste.com", primeiro, "senha-nova-123")
                .andExpect(status().isBadRequest());

        // o codigo novo continua valendo
        redefinir("reset.invalida@teste.com", segundo, "senha-nova-123")
                .andExpect(status().isOk());
    }

    @Test
    void novaSenhaCurta_retorna400DeValidacao() throws Exception {
        criarUsuario("reset.curta@teste.com", "senha-original");
        String codigo = codigoDemoDe(solicitar("reset.curta@teste.com"));

        redefinir("reset.curta@teste.com", codigo, "123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A senha deve ter ao menos 6 caracteres"));
    }
}
