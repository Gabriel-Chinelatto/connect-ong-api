package com.example.connectong_api.controller;

import com.example.connectong_api.dto.AssistenteRequestDTO;
import com.example.connectong_api.service.AssistenteDevService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recurso REST /assistente-dev: chatbot que explica COMO o Connect ONG foi
 * desenvolvido (tecnologias, metodos, decisoes, versoes). Reusa o mesmo
 * DTO/contrato do /assistente ({mensagem, historico}); a resposta e texto puro
 * ({@code AssistenteResponseDTO} com sugestoes vazias).
 *
 * PUBLICO (whitelist do SecurityConfig): aparece na tela "Sobre" dos frontends,
 * com ou sem login. Responde com IA (Groq) ancorada num documento curado; se a
 * IA falhar, cai no fallback por regras. Ver {@link AssistenteDevService}.
 */
@RestController
@RequestMapping("/assistente-dev")
@Tag(name = "Assistente de Desenvolvimento",
     description = "Explica como o Connect ONG foi feito (IA + fallback por regras)")
public class AssistenteDevController {

    @Autowired
    private AssistenteDevService assistenteDevService;

    @PostMapping
    @Operation(summary = "Perguntar sobre o desenvolvimento do projeto (publico)")
    public ResponseEntity<?> conversar(@Valid @RequestBody AssistenteRequestDTO dto) {
        return assistenteDevService.responder(dto);
    }
}
