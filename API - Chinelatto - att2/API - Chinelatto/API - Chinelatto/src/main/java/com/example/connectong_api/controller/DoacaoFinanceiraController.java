package com.example.connectong_api.controller;

import com.example.connectong_api.dto.DoacaoFinanceiraRequestDTO;
import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.service.DoacaoFinanceiraService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/doacoes-financeiras")
@Tag(name = "Doações Financeiras (PIX)",
        description = "Doação financeira simulada via PIX (sem gateway real)")
public class DoacaoFinanceiraController {

    @Autowired
    private DoacaoFinanceiraService service;

    @Autowired
    private SecurityUtils security;

    @PostMapping
    @Operation(summary = "Fazer uma doação financeira (PIX simulado) e gerar o comprovante")
    public ResponseEntity<?> doar(@Valid @RequestBody DoacaoFinanceiraRequestDTO dto) {
        // Doa em nome do proprio usuario autenticado, nunca de outro.
        security.exigirUsuario(dto.getDoadorId());
        return service.doar(dto);
    }

    @GetMapping
    @Operation(summary = "Listar doações financeiras (por doadorId ou ongId)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long doadorId,
            @RequestParam(required = false) Long ongId
    ) {
        if (doadorId != null) {
            // Historico de doacoes do doador e privado: so o proprio doador ve.
            security.exigirUsuario(doadorId);
            return ResponseEntity.ok(service.listarPorDoador(doadorId));
        }
        if (ongId != null) {
            // Doacoes recebidas pela ONG: so a propria ONG ve.
            security.exigirOng(ongId);
            return ResponseEntity.ok(service.listarPorOng(ongId));
        }
        return ResponseEntity.ok(java.util.Collections.emptyList());
    }
}
