package com.example.connectong_api.controller;

import com.example.connectong_api.dto.MensagemRequestDTO;
import com.example.connectong_api.service.MensagemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mensagens")
@CrossOrigin(origins = "*")
@Tag(name = "Mensagens (Chat)",
        description = "Chat entre doador e ONG dentro de um match aceito")
public class MensagemController {

    @Autowired
    private MensagemService service;

    @GetMapping
    @Operation(summary = "Listar as mensagens de um match (interesseId)")
    public ResponseEntity<?> listar(@RequestParam Long interesseId) {
        return ResponseEntity.ok(service.listar(interesseId));
    }

    @PostMapping
    @Operation(summary = "Enviar uma mensagem no chat de um match")
    public ResponseEntity<?> enviar(@RequestBody MensagemRequestDTO dto) {
        return service.enviar(dto);
    }
}
