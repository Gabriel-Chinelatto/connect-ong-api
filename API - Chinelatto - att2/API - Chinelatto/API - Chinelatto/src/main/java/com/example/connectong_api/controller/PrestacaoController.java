package com.example.connectong_api.controller;

import com.example.connectong_api.dto.PrestacaoRequestDTO;
import com.example.connectong_api.service.PrestacaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/prestacoes")
@Tag(name = "Prestação de Contas",
        description = "A ONG mostra ao doador o resultado da doação")
public class PrestacaoController {

    @Autowired
    private PrestacaoService service;

    @GetMapping
    @Operation(summary = "Listar prestações de contas de um match (interesseId)")
    public ResponseEntity<?> listar(@RequestParam Long interesseId) {
        return ResponseEntity.ok(service.listar(interesseId));
    }

    @PostMapping
    @Operation(summary = "Publicar uma prestação de contas num match")
    public ResponseEntity<?> criar(@Valid @RequestBody PrestacaoRequestDTO dto) {
        return service.criar(dto);
    }
}
