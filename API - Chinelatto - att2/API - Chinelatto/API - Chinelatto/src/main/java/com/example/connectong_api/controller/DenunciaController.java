package com.example.connectong_api.controller;

import com.example.connectong_api.dto.DenunciaRequestDTO;
import com.example.connectong_api.service.DenunciaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /denuncias: reportar ONG/usuario/conteudo (moderacao/credibilidade).
 * Permite registrar uma denuncia e, para moderacao, listar por status e marcar como resolvida.
 * Exige autenticacao (token JWT); a listagem/resolucao sao operacoes administrativas.
 */
@RestController
@RequestMapping("/denuncias")
@Tag(name = "Denuncias", description = "Reportar ONG/usuario/conteudo (moderacao/credibilidade)")
public class DenunciaController {

    @Autowired
    private DenunciaService service;

    @PostMapping
    @Operation(summary = "Registrar uma denuncia")
    public ResponseEntity<?> criar(@Valid @RequestBody DenunciaRequestDTO dto) {
        return service.criar(dto);
    }

    @GetMapping
    @Operation(summary = "Listar denuncias (admin; filtra por status ABERTA/RESOLVIDA)")
    public ResponseEntity<?> listar(@RequestParam(required = false) String status) {
        return ResponseEntity.ok(service.listar(status));
    }

    @PutMapping("/{id}/resolver")
    @Operation(summary = "Marcar denuncia como resolvida")
    public ResponseEntity<?> resolver(@PathVariable Long id) {
        return service.resolver(id);
    }
}
