package com.example.connectong_api.controller;

import com.example.connectong_api.dto.AuditLogResponseDTO;
import com.example.connectong_api.repository.AuditLogRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/audit-logs")
@CrossOrigin(origins = "*")
@Tag(name = "Auditoria",
        description = "Registro de eventos de seguranca (logins, cadastros, doacoes)")
public class AuditController {

    @Autowired
    private AuditLogRepository repository;

    @GetMapping
    @Operation(summary = "Listar registros de auditoria (mais recentes primeiro; filtra por usuarioId)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(defaultValue = "100") int limite
    ) {
        if (limite < 1) limite = 1;
        if (limite > 500) limite = 500;

        List<AuditLogResponseDTO> resultado;
        if (usuarioId != null) {
            resultado = repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId)
                    .stream().map(AuditLogResponseDTO::new).collect(Collectors.toList());
        } else {
            resultado = repository.findAllByOrderByCriadoEmDesc(PageRequest.of(0, limite))
                    .stream().map(AuditLogResponseDTO::new).collect(Collectors.toList());
        }
        return ResponseEntity.ok(resultado);
    }
}
