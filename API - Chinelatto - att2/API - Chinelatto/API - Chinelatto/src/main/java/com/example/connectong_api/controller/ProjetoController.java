package com.example.connectong_api.controller;

import com.example.connectong_api.model.Projeto;
import com.example.connectong_api.service.ProjetoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projetos")
@CrossOrigin(origins = "*")
@Tag(name = "Projetos", description = "Projetos/campanhas das ONGs")
public class ProjetoController {

    @Autowired
    private ProjetoService service;

    @GetMapping
    @Operation(summary = "Listar todos os projetos")
    public ResponseEntity<?> listar() {

        return ResponseEntity.ok(
                service.listar()
        );
    }

    @PostMapping
    @Operation(summary = "Cadastrar um novo projeto")
    public ResponseEntity<?> criar(
            @RequestBody Projeto projeto
    ) {

        return service.criar(projeto);
    }
}