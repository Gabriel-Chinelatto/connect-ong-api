package com.example.connectong_api.controller;

import com.example.connectong_api.dto.FreteRequestDTO;
import com.example.connectong_api.service.FreteService;
import com.example.connectong_api.service.RateLimitService;

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
 * Recurso REST /frete/estimar: estima o custo de envio de uma doacao entre duas
 * cidades (PUBLICO, na whitelist do SecurityConfig). Protegido por rate limiting
 * proprio (chave "frete"). Ver FreteService — valores SEMPRE estimados.
 */
@RestController
@RequestMapping("/frete")
@Tag(name = "Frete", description = "Estimativa de frete de doacoes (nao e cotacao oficial)")
public class FreteController {

    @Autowired private FreteService freteService;
    @Autowired private RateLimitService rateLimitService;

    @Value("${app.frete.ratelimit.max:40}")
    private int maxFrete;

    @PostMapping("/estimar")
    @Operation(summary = "Estimar frete de uma doacao entre duas cidades (publico)")
    public ResponseEntity<?> estimar(@Valid @RequestBody FreteRequestDTO req) {
        if (rateLimitService.excedeuSolicitacoes("frete", maxFrete)) {
            return RateLimitService.resposta429();
        }
        return ResponseEntity.ok(freteService.estimar(req));
    }
}
