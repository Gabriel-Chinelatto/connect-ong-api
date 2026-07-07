package com.example.connectong_api.service;

import com.example.connectong_api.dto.UsuarioResponseDTO;
import com.example.connectong_api.model.DoisFatoresCodigo;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.DoisFatoresCodigoRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Verificacao em duas etapas (2FA) no login — SIMULADA como o "esqueci a senha"
 * (nao ha SMTP no projeto; na feira o codigo volta como codigoDemo).
 *
 * Fluxo: quando a conta tem doisFatores=1, o POST /usuarios/login NAO devolve
 * tokens; chama {@link #gerarCodigo(Usuario)} (codigo de 6 digitos, 10 min,
 * invalida os anteriores) e responde {requer2fa:true,...}. O segundo passo,
 * POST /auth/login-2fa {email,codigo}, cai em {@link #verificarLogin} que valida
 * o codigo (existe / nao usado / nao expirado), marca usadoEm e emite os MESMOS
 * tokens do login normal. Forca bruta do codigo (1M combinacoes) e mitigada pelo
 * RateLimitService (5 erros por email -> bloqueio, como no redefinir-senha),
 * respondendo sempre o mesmo 400 generico.
 */
@Service
public class DoisFatoresService {

    private static final long VALIDADE_MINUTOS = 10;
    private static final String MENSAGEM_CODIGO_INVALIDO = "Código inválido ou expirado.";

    // SecureRandom (nao Random): codigo imprevisivel mesmo conhecendo o horario.
    private final SecureRandom random = new SecureRandom();

    @Autowired private DoisFatoresCodigoRepository repository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private JwtService jwtService;
    @Autowired private AuditService auditService;
    @Autowired private RateLimitService rateLimitService;

    /**
     * Gera um novo codigo 2FA para o usuario (invalidando os anteriores) e o
     * retorna. Chamado pelo login quando doisFatores=1; o valor volta ao cliente
     * apenas como codigoDemo (feira), nunca e auditado.
     */
    @Transactional
    public String gerarCodigo(Usuario u) {
        LocalDateTime agora = LocalDateTime.now();

        // So o codigo mais recente vale: invalida os pendentes.
        List<DoisFatoresCodigo> pendentes =
                repository.findByUsuarioIdAndUsadoEmIsNull(u.getId());
        pendentes.forEach(c -> c.setUsadoEm(agora));
        repository.saveAll(pendentes);

        String codigo = String.format("%06d", random.nextInt(1_000_000));

        DoisFatoresCodigo novo = new DoisFatoresCodigo();
        novo.setUsuarioId(u.getId());
        novo.setCodigo(codigo);
        novo.setExpiraEm(agora.plusMinutes(VALIDADE_MINUTOS));
        repository.save(novo);

        // Auditoria SEM o codigo (nunca logar codigo nem senha).
        auditService.registrar("LOGIN_2FA_DESAFIO", u.getId(),
                "Codigo 2FA gerado no login para: " + u.getEmail());

        return codigo;
    }

    /**
     * Segundo passo do login com 2FA: valida o codigo e, em caso de sucesso,
     * emite os MESMOS tokens/campos do login normal.
     */
    @Transactional
    public ResponseEntity<?> verificarLogin(String email, String codigo) {

        // Anti-forca-bruta do codigo: apos N erros para o mesmo email, tudo
        // responde o MESMO 400 generico (sem revelar o motivo).
        if (rateLimitService.bloqueadoPorFalhas("login-2fa", email)) {
            return erroCodigoInvalido();
        }

        Optional<Usuario> usuario = usuarioRepository.findByEmail(email);

        // Conta inexistente ou excluida (soft-delete): mesma falha generica.
        if (usuario.isEmpty() || usuario.get().getDataExclusao() != null) {
            return falha(email);
        }

        Usuario u = usuario.get();

        Optional<DoisFatoresCodigo> encontrado = repository
                .findTopByUsuarioIdAndCodigoAndUsadoEmIsNullOrderByIdDesc(u.getId(), codigo);

        // Codigo inexistente / ja usado / expirado: mesma falha generica.
        if (encontrado.isEmpty()
                || encontrado.get().getExpiraEm() == null
                || encontrado.get().getExpiraEm().isBefore(LocalDateTime.now())) {
            return falha(email);
        }

        // Sucesso: consome o codigo e emite os tokens.
        DoisFatoresCodigo c = encontrado.get();
        c.setUsadoEm(LocalDateTime.now());
        repository.save(c);

        rateLimitService.limparFalhas("login-2fa", email);

        UsuarioResponseDTO resposta = new UsuarioResponseDTO(
                u.getId(), u.getNome(), u.getEmail(), u.getTipo(), u.getOngId());
        resposta.setAccessToken(jwtService.gerarAccessToken(u));
        resposta.setRefreshToken(jwtService.gerarRefreshToken(u));

        auditService.registrar("LOGIN_2FA_SUCESSO", u.getId(),
                "Login com 2FA concluido: " + u.getEmail());

        return ResponseEntity.ok(resposta);
    }

    private ResponseEntity<?> falha(String email) {
        rateLimitService.registrarFalha("login-2fa", email);
        return erroCodigoInvalido();
    }

    private ResponseEntity<?> erroCodigoInvalido() {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", MENSAGEM_CODIGO_INVALIDO);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(erro);
    }
}
