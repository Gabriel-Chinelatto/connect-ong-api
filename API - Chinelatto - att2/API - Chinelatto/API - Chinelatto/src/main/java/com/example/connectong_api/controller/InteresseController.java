package com.example.connectong_api.controller;

import com.example.connectong_api.dto.InteresseRequestDTO;
import com.example.connectong_api.service.InteresseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interesses")
@CrossOrigin(origins = "*")
@Tag(name = "Interesses (Match)",
        description = "Doadores demonstram interesse; ONGs aceitam ou recusam")
public class InteresseController {

    @Autowired
    private InteresseService service;

    @GetMapping
    @Operation(summary = "Listar interesses (filtra por doadorId ou ongId)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long doadorId,
            @RequestParam(required = false) Long ongId
    ) {
        return ResponseEntity.ok(service.listar(doadorId, ongId));
    }

    @PostMapping
    @Operation(summary = "Doador demonstra interesse em uma necessidade")
    public ResponseEntity<?> demonstrar(
            @Valid @RequestBody InteresseRequestDTO dto
    ) {
        return service.demonstrarInteresse(dto);
    }

    @PutMapping("/{id}/aceitar")
    @Operation(summary = "ONG aceita o interesse (vira um match)")
    public ResponseEntity<?> aceitar(@PathVariable Long id) {
        return service.aceitar(id);
    }

    @PutMapping("/{id}/recusar")
    @Operation(summary = "ONG recusa o interesse")
    public ResponseEntity<?> recusar(@PathVariable Long id) {
        return service.recusar(id);
    }
}
