package com.example.connectong_api.controller;

import com.example.connectong_api.dto.BloqueioRequestDTO;
import com.example.connectong_api.service.BloqueioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /bloqueios: a ONG bloqueia/desbloqueia doadores. Exige conta de
 * ONG autenticada (o ongId vem do token); doador/admin recebem 403. O efeito e
 * a ONG "sumir" para o doador bloqueado (feed, busca, perfil e chat) — a regra
 * fica nos services de feed/mensagem, alimentados pelo BloqueioService.
 */
@RestController
@RequestMapping("/bloqueios")
@Tag(name = "Bloqueios",
        description = "ONG bloqueia doadores (a ONG some do feed/busca/chat do bloqueado)")
public class BloqueioController {

    @Autowired
    private BloqueioService service;

    @PostMapping
    @Operation(summary = "Bloquear um doador (idempotente; só conta de ONG)")
    public ResponseEntity<?> bloquear(@RequestBody BloqueioRequestDTO dto) {
        return service.bloquear(dto != null ? dto.getDoadorId() : null);
    }

    @DeleteMapping("/{doadorId}")
    @Operation(summary = "Desbloquear um doador (idempotente; só conta de ONG)")
    public ResponseEntity<?> desbloquear(@PathVariable Long doadorId) {
        return service.desbloquear(doadorId);
    }

    @GetMapping
    @Operation(summary = "Listar os doadores bloqueados pela PRÓPRIA ONG autenticada")
    public ResponseEntity<?> listar() {
        return service.listar();
    }
}
