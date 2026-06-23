package com.example.connectong_api.controller;

import com.example.connectong_api.model.Ong;
import com.example.connectong_api.service.ONGService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ongs")
@CrossOrigin(origins = "*")
@Tag(name = "ONGs", description = "Cadastro, busca e gerenciamento de ONGs")
public class ONGController {

    @Autowired
    private ONGService service;

    @GetMapping
    @Operation(summary = "Listar ONGs (filtra por nome com o parametro 'nome')")
    public ResponseEntity<?> listar(
            @RequestParam(required = false)
            String nome
    ) {

        return ResponseEntity.ok(
                service.listar(nome)
        );
    }

    @PostMapping
    @Operation(summary = "Cadastrar uma nova ONG")
    public ResponseEntity<?> criar(
            @RequestBody Ong ong
    ) {

        return service.criar(ong);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar uma ONG existente")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @RequestBody Ong ong
    ) {

        return service.atualizar(
                id,
                ong
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir uma ONG")
    public ResponseEntity<?> deletar(
            @PathVariable Long id
    ) {

        return service.deletar(id);
    }
}