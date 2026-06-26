package com.example.connectong_api.controller;

import com.example.connectong_api.dto.CampanhaRequestDTO;
import com.example.connectong_api.service.CampanhaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Recurso REST /campanhas: campanhas de arrecadacao das ONGs (meta + progresso).
 * Permite listar/destacar campanhas, criar, contribuir com um valor e encerrar.
 * Exige autenticacao (token JWT), salvo o que estiver na whitelist publica.
 */
@RestController
@RequestMapping("/campanhas")
@Tag(name = "Campanhas", description = "Campanhas de arrecadacao das ONGs (meta + progresso)")
public class CampanhaController {

    @Autowired
    private CampanhaService service;

    @GetMapping
    @Operation(summary = "Listar campanhas (filtra por ongId; abertas=true esconde as encerradas)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long ongId,
            @RequestParam(defaultValue = "false") boolean abertas
    ) {
        return ResponseEntity.ok(service.listar(ongId, abertas));
    }

    @GetMapping("/destaques")
    @Operation(summary = "Listar campanhas em destaque (abertas)")
    public ResponseEntity<?> destaques() {
        return ResponseEntity.ok(service.destaques());
    }

    @PostMapping
    @Operation(summary = "Criar uma campanha (de uma ONG)")
    public ResponseEntity<?> criar(@Valid @RequestBody CampanhaRequestDTO dto) {
        return service.criar(dto);
    }

    @PostMapping("/{id}/contribuir")
    @Operation(summary = "Contribuir com um valor para a campanha (atualiza o progresso)")
    public ResponseEntity<?> contribuir(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        Double valor = body.get("valor") != null
                ? Double.valueOf(body.get("valor").toString()) : null;
        String doadorNome = body.get("doadorNome") != null
                ? body.get("doadorNome").toString() : null;
        return service.contribuir(id, valor, doadorNome);
    }

    @PutMapping("/{id}/encerrar")
    @Operation(summary = "Encerrar uma campanha")
    public ResponseEntity<?> encerrar(@PathVariable Long id) {
        return service.encerrar(id);
    }
}
