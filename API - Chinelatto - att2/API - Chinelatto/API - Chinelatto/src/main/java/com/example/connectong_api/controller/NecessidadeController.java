package com.example.connectong_api.controller;

import com.example.connectong_api.dto.NecessidadeRequestDTO;
import com.example.connectong_api.dto.NecessidadeUpdateDTO;
import com.example.connectong_api.service.NecessidadeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /necessidades: necessidades publicadas pelas ONGs (alvo do interesse do doador).
 * Permite listar (filtrando por ongId ou status) e publicar uma nova necessidade de uma ONG.
 * Exige autenticacao (token JWT), salvo o que estiver na whitelist publica.
 */
@RestController
@RequestMapping("/necessidades")
@Tag(name = "Necessidades", description = "Necessidades publicadas pelas ONGs")
public class NecessidadeController {

    @Autowired
    private NecessidadeService service;

    @GetMapping
    @Operation(summary = "Listar necessidades (filtra por ongId, status ou categoria)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long ongId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoria
    ) {
        return ResponseEntity.ok(service.listar(ongId, status, categoria));
    }

    @PostMapping
    @Operation(summary = "Publicar uma nova necessidade (de uma ONG)")
    public ResponseEntity<?> criar(
            @Valid @RequestBody NecessidadeRequestDTO dto
    ) {
        return service.criar(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar uma necessidade (somente a ONG dona)")
    public ResponseEntity<?> editar(
            @PathVariable Long id,
            @Valid @RequestBody NecessidadeUpdateDTO dto
    ) {
        // A checagem de dono (a ONG da necessidade) e feita no service.
        return service.atualizar(id, dto);
    }
}
