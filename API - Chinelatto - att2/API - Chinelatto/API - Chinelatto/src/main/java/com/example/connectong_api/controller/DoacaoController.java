package com.example.connectong_api.controller;

import com.example.connectong_api.model.Doacao;
import com.example.connectong_api.service.DoacaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/doacoes")
@Tag(name = "Doações", description = "Cadastro e gerenciamento de doações")
public class DoacaoController {

    @Autowired
    private DoacaoService service;

    @GetMapping
    @Operation(summary = "Listar todas as doações")
    public ResponseEntity<?> listar() {

        return ResponseEntity.ok(
                service.listar()
        );
    }

    @PostMapping
    @Operation(summary = "Cadastrar uma nova doação")
    public ResponseEntity<?> criar(
            @RequestBody Doacao doacao
    ) {

        return service.criar(doacao);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar uma doação existente")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @RequestBody Doacao doacao
    ) {

        return service.atualizar(id, doacao);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir uma doação")
    public ResponseEntity<?> deletar(
            @PathVariable Long id
    ) {

        return service.deletar(id);
    }
}