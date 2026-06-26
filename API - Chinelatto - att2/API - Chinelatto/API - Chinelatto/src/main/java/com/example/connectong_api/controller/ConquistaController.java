package com.example.connectong_api.controller;

import com.example.connectong_api.service.ConquistaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/conquistas")
@Tag(name = "Conquistas", description = "Conquistas (gamificacao) de doadores e ONGs")
public class ConquistaController {

    @Autowired
    private ConquistaService service;

    @GetMapping("/doador/{usuarioId}")
    @Operation(summary = "Conquistas de um doador (lista completa com estado)")
    public ResponseEntity<?> doador(@PathVariable Long usuarioId) {
        return service.doador(usuarioId);
    }

    @GetMapping("/ong/{ongId}")
    @Operation(summary = "Conquistas de uma ONG (lista completa com estado)")
    public ResponseEntity<?> ong(@PathVariable Long ongId) {
        return service.ong(ongId);
    }
}
