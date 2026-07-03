package com.example.connectong_api.service;

import com.example.connectong_api.dto.EsqueciSenhaDTO;
import com.example.connectong_api.dto.RedefinirSenhaDTO;
import com.example.connectong_api.model.SenhaReset;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.SenhaResetRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fluxo "esqueci a senha" (contrato fixo com os frontends):
 *
 * POST /auth/esqueci-senha  -> SEMPRE 200 com mensagem generica (anti-enumeracao:
 *   email inexistente ou conta soft-deleted recebem exatamente a mesma resposta,
 *   sem gerar codigo). Quando o usuario existe, gera codigo de 6 digitos valido
 *   por 15 minutos e invalida os codigos anteriores nao usados.
 *
 * POST /auth/redefinir-senha -> valida (existe, nao usado, nao expirado, pertence
 *   ao email), marca usadoEm e grava a nova senha com BCrypt. QUALQUER falha vira
 *   400 generico ("Código inválido ou expirado."), sem distinguir o motivo.
 *
 * SIMULACAO DE E-MAIL (feira): nao ha servidor SMTP disponivel no projeto, entao
 * quando app.demo.enabled=true a resposta inclui o campo extra "codigoDemo" com
 * o codigo gerado — mesmo precedente do PIX simulado. Em producao real, basta
 * desligar o demo (APP_DEMO_ENABLED=false) e integrar um servico de e-mail; o
 * contrato dos frontends nao muda (codigoDemo simplesmente deixa de existir).
 */
@Service
public class SenhaResetService {

    private static final long VALIDADE_MINUTOS = 15;
    private static final String MENSAGEM_SOLICITACAO =
            "Se o e-mail existir, enviaremos um código de recuperação.";
    private static final String MENSAGEM_CODIGO_INVALIDO =
            "Código inválido ou expirado.";

    // SecureRandom (e nao Random): codigo imprevisivel mesmo conhecendo o horario.
    private final SecureRandom random = new SecureRandom();

    @Autowired private SenhaResetRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private AuditService auditService;
    @Autowired private RateLimitService rateLimitService;

    @Value("${app.demo.enabled:true}")
    private boolean demoEnabled;

    // =========================
    // SOLICITAR CODIGO (esqueci a senha)
    // =========================
    @Transactional
    public ResponseEntity<?> solicitar(EsqueciSenhaDTO dto) {

        // Anti-spam de codigo: ~5 solicitacoes por IP a cada 15 minutos.
        if (rateLimitService.excedeuSolicitacoes("esqueci-senha")) {
            return RateLimitService.resposta429();
        }

        Map<String, String> resposta = new LinkedHashMap<>();
        resposta.put("mensagem", MENSAGEM_SOLICITACAO);

        Optional<Usuario> usuario = usuarioRepository.findByEmail(dto.getEmail());

        // Anti-enumeracao: email inexistente OU conta excluida (soft-delete)
        // recebem a MESMA resposta 200, sem gerar codigo (nem codigoDemo).
        if (usuario.isEmpty() || usuario.get().getDataExclusao() != null) {
            return ResponseEntity.ok(resposta);
        }

        Usuario u = usuario.get();
        LocalDateTime agora = LocalDateTime.now();

        // Invalida os codigos anteriores ainda nao usados: so o codigo mais
        // recente do usuario funciona (evita acumular codigos vivos).
        List<SenhaReset> pendentes = repository.findByUsuarioIdAndUsadoEmIsNull(u.getId());
        pendentes.forEach(r -> r.setUsadoEm(agora));
        repository.saveAll(pendentes);

        String codigo = String.format("%06d", random.nextInt(1_000_000));

        SenhaReset reset = new SenhaReset();
        reset.setUsuarioId(u.getId());
        reset.setCodigo(codigo);
        reset.setExpiraEm(agora.plusMinutes(VALIDADE_MINUTOS));
        repository.save(reset);

        // Auditoria SEM o codigo (nunca logar codigo nem senha).
        auditService.registrar("SENHA_RESET_SOLICITADO", u.getId(),
                "Codigo de recuperacao de senha gerado para: " + u.getEmail());

        // Simulacao de envio de e-mail para a feira (ver javadoc da classe).
        if (demoEnabled) {
            resposta.put("codigoDemo", codigo);
        }

        return ResponseEntity.ok(resposta);
    }

    // =========================
    // REDEFINIR SENHA (com o codigo recebido)
    // =========================
    @Transactional
    public ResponseEntity<?> redefinir(RedefinirSenhaDTO dto) {

        // Forca bruta do codigo (1M de combinacoes): apos N tentativas erradas
        // para o mesmo email, tudo passa a responder o MESMO 400 generico do
        // contrato (sem 429 aqui, para nao criar um terceiro status na rota).
        if (rateLimitService.bloqueadoPorFalhas("redefinir-senha", dto.getEmail())) {
            return erroCodigoInvalido();
        }

        Optional<Usuario> usuario = usuarioRepository.findByEmail(dto.getEmail());

        // Conta inexistente ou excluida: mesma falha generica (sem vazar motivo).
        if (usuario.isEmpty() || usuario.get().getDataExclusao() != null) {
            return falha(dto.getEmail());
        }

        Usuario u = usuario.get();

        Optional<SenhaReset> reset = repository
                .findTopByUsuarioIdAndCodigoAndUsadoEmIsNullOrderByIdDesc(u.getId(), dto.getCodigo());

        // Codigo inexistente/ja usado/expirado: mesma falha generica.
        if (reset.isEmpty()
                || reset.get().getExpiraEm() == null
                || reset.get().getExpiraEm().isBefore(LocalDateTime.now())) {
            return falha(dto.getEmail());
        }

        // Sucesso: consome o codigo e grava a nova senha com BCrypt.
        SenhaReset r = reset.get();
        r.setUsadoEm(LocalDateTime.now());
        repository.save(r);

        u.setSenha(passwordEncoder.encode(dto.getNovaSenha()));
        usuarioRepository.save(u);

        rateLimitService.limparFalhas("redefinir-senha", dto.getEmail());

        // Auditoria SEM o codigo e SEM a senha.
        auditService.registrar("SENHA_REDEFINIDA", u.getId(),
                "Senha redefinida via codigo de recuperacao para: " + u.getEmail());

        Map<String, String> ok = new HashMap<>();
        ok.put("mensagem", "Senha redefinida com sucesso.");
        return ResponseEntity.ok(ok);
    }

    private ResponseEntity<?> falha(String email) {
        rateLimitService.registrarFalha("redefinir-senha", email);
        return erroCodigoInvalido();
    }

    private ResponseEntity<?> erroCodigoInvalido() {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", MENSAGEM_CODIGO_INVALIDO);
        return ResponseEntity.badRequest().body(erro);
    }
}
