package com.example.connectong_api.controller;

import com.example.connectong_api.dto.AssistenteRequestDTO;
import com.example.connectong_api.service.AssistenteService;
import com.example.connectong_api.service.RateLimitService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recurso REST /assistente: chatbot que ajuda o DOADOR a decidir para quem doar.
 *
 * PUBLICO (esta na whitelist do SecurityConfig): funciona sem login. Se o doador
 * estiver autenticado, o filtro JWT ainda popula o SecurityContext e o service
 * usa a cidade do perfil para priorizar ONGs/necessidades proximas — degradando
 * de forma graciosa para o anonimo (que pode informar a cidade no body).
 *
 * Responde com IA gratuita (Groq) quando ha chave configurada; caso contrario
 * (ou se a IA falhar/timeout/429) responde por REGRAS locais. Ver AssistenteService.
 */
@RestController
@RequestMapping("/assistente")
@Tag(name = "Assistente", description = "Assistente de doacao (IA + fallback por regras)")
public class AssistenteController {

    @Autowired
    private AssistenteService assistenteService;

    @Autowired
    private RateLimitService rateLimitService;

    @Value("${app.ia.sugestoes.ratelimit.max:40}")
    private int maxSugestoes;

    @PostMapping
    @Operation(summary = "Conversar com o assistente de doacao (publico)")
    public ResponseEntity<?> conversar(@Valid @RequestBody AssistenteRequestDTO dto) {
        return assistenteService.responder(dto);
    }

    /**
     * Sugestoes PROATIVAS para o doador (SEM mensagem de chat). Usa o token, se
     * houver, para pegar a cidade e o historico do doador. PUBLICO (na whitelist),
     * com rate limiting proprio (chave "sugestoes"). Ver AssistenteService.
     */
    @PostMapping("/sugestoes")
    @Operation(summary = "Sugestoes proativas de doacao para o doador (publico)")
    public ResponseEntity<?> sugestoes() {
        if (rateLimitService.excedeuSolicitacoes("sugestoes", maxSugestoes)) {
            return RateLimitService.resposta429();
        }
        return assistenteService.sugerirParaDoador();
    }
}
