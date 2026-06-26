package com.example.connectong_api.controller;

import com.example.connectong_api.service.NotificacaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notificacoes")
@Tag(name = "Notificações", description = "Notificações dos usuários")
public class NotificacaoController {

    @Autowired
    private NotificacaoService service;

    @GetMapping
    @Operation(summary = "Listar notificações de um usuário")
    public ResponseEntity<?> listar(@RequestParam Long usuarioId) {
        return ResponseEntity.ok(service.listar(usuarioId));
    }

    @GetMapping("/nao-lidas")
    @Operation(summary = "Contar notificações não lidas")
    public ResponseEntity<?> contarNaoLidas(@RequestParam Long usuarioId) {
        return service.contarNaoLidas(usuarioId);
    }

    @PutMapping("/{id}/lida")
    @Operation(summary = "Marcar uma notificação como lida")
    public ResponseEntity<?> marcarLida(@PathVariable Long id) {
        return service.marcarLida(id);
    }

    @PutMapping("/marcar-todas")
    @Operation(summary = "Marcar todas as notificações como lidas")
    public ResponseEntity<?> marcarTodas(@RequestParam Long usuarioId) {
        return service.marcarTodas(usuarioId);
    }
}
