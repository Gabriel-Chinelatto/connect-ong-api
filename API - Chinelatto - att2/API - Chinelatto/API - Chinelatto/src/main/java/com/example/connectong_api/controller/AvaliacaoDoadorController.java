package com.example.connectong_api.controller;

import com.example.connectong_api.dto.AvaliacaoDoadorRequestDTO;
import com.example.connectong_api.service.AvaliacaoDoadorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /avaliacoes-doador: ONGs avaliam DOADORES (nota de 1 a 5) — o
 * espelho de /avaliacoes. O POST exige uma conta de ONG (a avaliadora vem do
 * token) e faz UPSERT por par ong+doador; o GET por doadorId e PUBLICO (esta
 * na whitelist do SecurityConfig) porque alimenta o perfil publico do doador.
 */
@RestController
@RequestMapping("/avaliacoes-doador")
@Tag(name = "Avaliações de Doador", description = "ONGs avaliam doadores (nota de 1 a 5)")
public class AvaliacaoDoadorController {

    @Autowired
    private AvaliacaoDoadorService service;

    @GetMapping
    @Operation(summary = "Listar avaliações recebidas por um doador (PÚBLICO)")
    public ResponseEntity<?> listar(@RequestParam Long doadorId) {
        return ResponseEntity.ok(service.listar(doadorId));
    }

    @PostMapping
    @Operation(summary = "ONG avalia um doador (cria ou atualiza a avaliação; recalcula a média do doador)")
    public ResponseEntity<?> avaliar(@Valid @RequestBody AvaliacaoDoadorRequestDTO dto) {
        // So ONG autenticada; a identidade da ONG vem do token (service).
        return service.avaliar(dto);
    }
}
