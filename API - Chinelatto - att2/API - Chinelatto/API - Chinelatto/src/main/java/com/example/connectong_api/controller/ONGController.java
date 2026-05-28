package com.example.connectong_api.controller;

import com.example.connectong_api.model.Ong;
import com.example.connectong_api.service.ONGService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ongs")
@CrossOrigin(origins = "*")
public class ONGController {

    @Autowired
    private ONGService service;

    // =========================
    // LISTAR
    // =========================
    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(required = false)
            String nome
    ) {

        return ResponseEntity.ok(
                service.listar(nome)
        );
    }

    // =========================
    // CRIAR
    // =========================
    @PostMapping
    public ResponseEntity<?> criar(
            @RequestBody Ong ong
    ) {

        return service.criar(ong);
    }

    // =========================
    // ATUALIZAR
    // =========================
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @RequestBody Ong ong
    ) {

        return service.atualizar(
                id,
                ong
        );
    }

    // =========================
    // DELETAR
    // =========================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletar(
            @PathVariable Long id
    ) {

        return service.deletar(id);
    }
}