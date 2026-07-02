package com.example.connectong_api.controller;

import com.example.connectong_api.dto.MensagemRequestDTO;
import com.example.connectong_api.service.MensagemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /mensagens: chat entre doador e ONG dentro de um match.
 * O chat so existe apos o interesse virar match ACEITO; mensagens sao listadas/enviadas
 * pelo interesseId. Exige autenticacao (token JWT); o vinculo ao match e validado no service.
 */
@RestController
@RequestMapping("/mensagens")
@Tag(name = "Mensagens (Chat)",
        description = "Chat entre doador e ONG dentro de um match aceito")
public class MensagemController {

    @Autowired
    private MensagemService service;

    @GetMapping
    @Operation(summary = "Listar as mensagens de um match (interesseId); marca as recebidas como lidas")
    public ResponseEntity<?> listar(@RequestParam Long interesseId) {
        return ResponseEntity.ok(service.listar(interesseId));
    }

    @PostMapping
    @Operation(summary = "Enviar uma mensagem no chat de um match")
    public ResponseEntity<?> enviar(@Valid @RequestBody MensagemRequestDTO dto) {
        return service.enviar(dto);
    }

    @GetMapping("/status")
    @Operation(summary = "Situacao do outro participante (online / visto por ultimo / digitando); tambem registra presenca de quem consulta")
    public ResponseEntity<?> status(@RequestParam Long interesseId) {
        return ResponseEntity.ok(service.status(interesseId));
    }

    @PostMapping("/digitando")
    @Operation(summary = "Sinaliza que o usuario esta digitando neste match (heartbeat)")
    public ResponseEntity<?> digitando(@RequestParam Long interesseId) {
        service.registrarDigitando(interesseId);
        return ResponseEntity.noContent().build();
    }
}
