package com.example.connectong_api.controller;

import com.example.connectong_api.dto.DoacaoFinanceiraRequestDTO;
import com.example.connectong_api.service.DoacaoFinanceiraService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/doacoes-financeiras")
@CrossOrigin(origins = "*")
@Tag(name = "Doações Financeiras (PIX)",
        description = "Doação financeira simulada via PIX (sem gateway real)")
public class DoacaoFinanceiraController {

    @Autowired
    private DoacaoFinanceiraService service;

    @PostMapping
    @Operation(summary = "Fazer uma doação financeira (PIX simulado) e gerar o comprovante")
    public ResponseEntity<?> doar(@Valid @RequestBody DoacaoFinanceiraRequestDTO dto) {
        return service.doar(dto);
    }

    @GetMapping
    @Operation(summary = "Listar doações financeiras (por doadorId ou ongId)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long doadorId,
            @RequestParam(required = false) Long ongId
    ) {
        if (doadorId != null) {
            return ResponseEntity.ok(service.listarPorDoador(doadorId));
        }
        if (ongId != null) {
            return ResponseEntity.ok(service.listarPorOng(ongId));
        }
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }
}
