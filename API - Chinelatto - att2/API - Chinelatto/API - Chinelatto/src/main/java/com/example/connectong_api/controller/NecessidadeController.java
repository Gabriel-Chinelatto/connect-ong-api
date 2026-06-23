package com.example.connectong_api.controller;

import com.example.connectong_api.dto.NecessidadeRequestDTO;
import com.example.connectong_api.service.NecessidadeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/necessidades")
@CrossOrigin(origins = "*")
@Tag(name = "Necessidades", description = "Necessidades publicadas pelas ONGs")
public class NecessidadeController {

    @Autowired
    private NecessidadeService service;

    @GetMapping
    @Operation(summary = "Listar necessidades (filtra por ongId ou status)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long ongId,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(service.listar(ongId, status));
    }

    @PostMapping
    @Operation(summary = "Publicar uma nova necessidade (de uma ONG)")
    public ResponseEntity<?> criar(
            @RequestBody NecessidadeRequestDTO dto
    ) {
        return service.criar(dto);
    }
}
