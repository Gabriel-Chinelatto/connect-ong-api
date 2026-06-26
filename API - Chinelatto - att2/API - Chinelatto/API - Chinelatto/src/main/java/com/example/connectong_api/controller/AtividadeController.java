package com.example.connectong_api.controller;

import com.example.connectong_api.service.AtividadeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/atividades")
@CrossOrigin(origins = "*")
@Tag(name = "Atividades", description = "Feed global de atividades recentes da plataforma (Timeline)")
public class AtividadeController {

    @Autowired
    private AtividadeService service;

    @GetMapping
    @Operation(summary = "Listar atividades recentes (feed global; filtra por ongId; limit padrao 30)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long ongId,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ResponseEntity.ok(service.listar(ongId, limit));
    }
}
