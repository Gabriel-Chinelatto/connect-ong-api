package com.example.connectong_api.controller;

import com.example.connectong_api.dto.RedacaoRequestDTO;
import com.example.connectong_api.dto.ResumoImpactoRequestDTO;
import com.example.connectong_api.service.RateLimitService;
import com.example.connectong_api.service.RedacaoService;
import com.example.connectong_api.service.ResumoImpactoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recursos REST /ia/*: apoios de IA (com fallback por regras) que funcionam mesmo
 * sem chave da Groq. PUBLICOS (na whitelist do SecurityConfig), cada um com rate
 * limiting proprio.
 *
 *  - POST /ia/redacao        : reescreve a necessidade de uma ONG (painel da ONG).
 *  - POST /ia/resumo-impacto : resume o impacto de uma ONG para o doador.
 */
@RestController
@RequestMapping("/ia")
@Tag(name = "IA", description = "Apoios de IA (redacao, resumo de impacto) com fallback por regras")
public class IaController {

    @Autowired private RedacaoService redacaoService;
    @Autowired private ResumoImpactoService resumoImpactoService;
    @Autowired private RateLimitService rateLimitService;

    @Value("${app.ia.redacao.ratelimit.max:30}")
    private int maxRedacao;

    @Value("${app.ia.resumo.ratelimit.max:30}")
    private int maxResumo;

    @PostMapping("/redacao")
    @Operation(summary = "Reescrever uma necessidade da ONG (publico)")
    public ResponseEntity<?> redacao(@Valid @RequestBody RedacaoRequestDTO req) {
        if (rateLimitService.excedeuSolicitacoes("redacao", maxRedacao)) {
            return RateLimitService.resposta429();
        }
        // Regra: pelo menos rascunho OU titulo deve vir preenchido.
        boolean semTitulo = req.getTitulo() == null || req.getTitulo().isBlank();
        boolean semRascunho = req.getRascunho() == null || req.getRascunho().isBlank();
        if (semTitulo && semRascunho) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("erro", "Informe um título ou um rascunho."));
        }
        return ResponseEntity.ok(redacaoService.redigir(req));
    }

    @PostMapping("/resumo-impacto")
    @Operation(summary = "Resumir o impacto de uma ONG para o doador (publico)")
    public ResponseEntity<?> resumoImpacto(@Valid @RequestBody ResumoImpactoRequestDTO req) {
        if (rateLimitService.excedeuSolicitacoes("resumo", maxResumo)) {
            return RateLimitService.resposta429();
        }
        return ResponseEntity.ok(resumoImpactoService.resumir(req.getOngId()));
    }
}
