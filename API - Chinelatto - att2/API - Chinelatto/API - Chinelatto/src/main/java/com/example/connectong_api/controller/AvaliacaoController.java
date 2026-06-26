package com.example.connectong_api.controller;

import com.example.connectong_api.dto.AvaliacaoRequestDTO;
import com.example.connectong_api.service.AvaliacaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/avaliacoes")
@CrossOrigin(origins = "*")
@Tag(name = "Avaliações", description = "Doadores avaliam ONGs (nota de 1 a 5)")
public class AvaliacaoController {

    @Autowired
    private AvaliacaoService service;

    @GetMapping
    @Operation(summary = "Listar avaliações de uma ONG (ongId)")
    public ResponseEntity<?> listar(@RequestParam Long ongId) {
        return ResponseEntity.ok(service.listar(ongId));
    }

    @PostMapping
    @Operation(summary = "Avaliar uma ONG (cria ou atualiza a avaliação do doador)")
    public ResponseEntity<?> avaliar(@Valid @RequestBody AvaliacaoRequestDTO dto) {
        return service.avaliar(dto);
    }
}
