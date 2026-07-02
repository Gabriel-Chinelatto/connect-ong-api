package com.example.connectong_api.config;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Provisiona a conta ADMIN no startup a partir de configuracao — NUNCA por uma
 * rota publica de cadastro (admin nao e auto-provisionavel, ao contrario de
 * DOADOR/ONG). Assim o papel privilegiado ROLE_ADMIN so existe se o operador
 * definir as credenciais no ambiente.
 *
 * Credenciais:
 * - Producao: variaveis de ambiente APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD
 *   (o Spring mapeia para app.admin.email / app.admin.password).
 * - Desenvolvimento: application-local.properties (gitignored).
 *
 * Comportamento seguro:
 * - Se email OU senha estiverem vazios, NAO cria nada (sem admin default/backdoor).
 * - Idempotente: so cria se ainda nao existir um usuario com aquele email; nunca
 *   reescreve a senha de um admin existente a cada boot.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UsuarioRepository usuarioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    public AdminBootstrap(UsuarioRepository usuarioRepository,
                          BCryptPasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail == null || adminEmail.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            // Sem credenciais configuradas: nao cria admin (evita backdoor padrao).
            return;
        }

        if (usuarioRepository.findByEmail(adminEmail).isPresent()) {
            return; // ja existe: nao mexe (nao reescreve senha)
        }

        Usuario admin = new Usuario();
        admin.setNome("Administrador");
        admin.setEmail(adminEmail);
        admin.setTipo("ADMIN");
        admin.setSenha(passwordEncoder.encode(adminPassword));
        usuarioRepository.save(admin);

        log.info("Conta ADMIN provisionada para o email configurado ({}).", adminEmail);
    }
}
