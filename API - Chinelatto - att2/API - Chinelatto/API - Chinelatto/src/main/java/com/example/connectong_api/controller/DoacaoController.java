package com.example.connectong_api.controller;

import com.example.connectong_api.model.Doacao;
import com.example.connectong_api.service.DoacaoService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/doacoes")
@CrossOrigin(origins = "*")
public class DoacaoController {

    @Autowired
    private DoacaoService service;

    // =========================
    // LISTAR
    // =========================
    @GetMapping
    public ResponseEntity<?> listar() {

        return ResponseEntity.ok(
                service.listar()
        );
    }

    // =========================
    // CRIAR
    // =========================
    @PostMapping
    public ResponseEntity<?> criar(
            @RequestBody Doacao doacao
    ) {

        return service.criar(doacao);
    }

    // =========================
    // ATUALIZAR
    // =========================
    @PutMapping("/{id}")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @RequestBody Doacao doacao
    ) {

        return service.atualizar(id, doacao);
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