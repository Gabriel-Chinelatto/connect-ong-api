package com.example.connectong_api.controller;

import com.example.connectong_api.model.Projeto;
import com.example.connectong_api.service.ProjetoService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projetos")
@CrossOrigin(origins = "*")
public class ProjetoController {

    @Autowired
    private ProjetoService service;

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
            @RequestBody Projeto projeto
    ) {

        return service.criar(projeto);
    }
}