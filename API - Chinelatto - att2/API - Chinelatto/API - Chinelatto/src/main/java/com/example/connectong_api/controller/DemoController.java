package com.example.connectong_api.controller;

import com.example.connectong_api.service.DemoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * "Modo Feira": carrega dados demonstrativos para a apresentacao.
 *
 * O seed cria contas de demonstracao com senha fixa (demo123), inclusive uma ONG
 * "verificada". E util na feira, mas um risco em producao. Fica atras do
 * interruptor "app.demo.enabled" (default true): em producao basta definir
 * APP_DEMO_ENABLED=false para desativar sem recompilar. Continua exigindo
 * autenticacao (o desktop chama pelo painel da ONG).
 */
@RestController
@RequestMapping("/demo")
@Tag(name = "Modo Feira", description = "Carrega dados demonstrativos (apresentacao)")
public class DemoController {

    @Autowired
    private DemoService demoService;

    @Value("${app.demo.enabled:true}")
    private boolean demoEnabled;

    @PostMapping("/seed")
    @Operation(summary = "Carrega dados demonstrativos (idempotente; senha padrao demo123)")
    public ResponseEntity<Map<String, Object>> seed() {
        if (!demoEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("erro", "O modo demonstracao esta desativado."));
        }
        return ResponseEntity.ok(demoService.carregar());
    }
}
