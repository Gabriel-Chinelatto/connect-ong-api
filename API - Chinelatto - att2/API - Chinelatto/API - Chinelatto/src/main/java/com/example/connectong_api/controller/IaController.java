package com.example.connectong_api.controller;

import com.example.connectong_api.dto.RedacaoRequestDTO;
import com.example.connectong_api.dto.ResumoImpactoRequestDTO;
import com.example.connectong_api.dto.SobreOngRequestDTO;
import com.example.connectong_api.service.ProvedorIA;
import com.example.connectong_api.service.RateLimitService;
import com.example.connectong_api.service.RedacaoService;
import com.example.connectong_api.service.ResumoImpactoService;
import com.example.connectong_api.service.SobreOngService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Recursos REST /ia/*: apoios de IA (com fallback por regras) que funcionam mesmo
 * sem chave da Groq. PUBLICOS (na whitelist do SecurityConfig), cada um com rate
 * limiting proprio.
 *
 *  - POST /ia/redacao        : reescreve a necessidade de uma ONG (painel da ONG).
 *  - POST /ia/resumo-impacto : resume o impacto de uma ONG para o doador.
 *  - POST /ia/sobre-ong      : escreve/refina o "Sobre" institucional da ONG (loop de ajuste).
 */
@RestController
@RequestMapping("/ia")
@Tag(name = "IA", description = "Apoios de IA (redacao, resumo de impacto, sobre da ONG) com fallback por regras")
public class IaController {

    @Autowired private RedacaoService redacaoService;
    @Autowired private ResumoImpactoService resumoImpactoService;
    @Autowired private SobreOngService sobreOngService;
    @Autowired private RateLimitService rateLimitService;
    @Autowired private ProvedorIA provedorIA;

    @Value("${app.ia.redacao.ratelimit.max:30}")
    private int maxRedacao;

    @Value("${app.ia.resumo.ratelimit.max:30}")
    private int maxResumo;

    @Value("${app.ia.sobre.ratelimit.max:30}")
    private int maxSobre;

    /**
     * Diagnostico da IA — responde se a chave esta configurada NAQUELE ambiente
     * (local ou Render), quais modelos estao na cadeia e qual foi o ultimo erro
     * observado. Existe porque a IA ja ficou dias inteira em "Modo basico" sem
     * sinal nenhum na tela: o assistente cai no fallback por regras em silencio.
     *
     * {@code ?ping=true} faz uma chamada MINIMA de verdade a Groq (poucos tokens)
     * para confirmar, antes da apresentacao, que a IA responde mesmo.
     *
     * NUNCA devolve a chave — so se ela existe.
     */
    @GetMapping("/status")
    @Operation(summary = "Diagnostico da IA: chave, modelos e ultimo erro (publico, sem segredo)")
    public ResponseEntity<?> status(@RequestParam(defaultValue = "false") boolean ping) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        boolean temChave = provedorIA.disponivel();
        corpo.put("chaveConfigurada", temChave);
        corpo.put("modelos", provedorIA.modelos());
        corpo.put("modeloVisao", provedorIA.modeloVisao());
        if (ping) {
            if (rateLimitService.excedeuSolicitacoes("ia-status-ping", 10)) {
                return RateLimitService.resposta429();
            }
            corpo.put("ping", provedorIA.ping() ? "ok" : "falhou");
        }
        // Lidos DEPOIS do ping, senao mostrariam o estado anterior a ele.
        corpo.put("ultimoModeloOk", provedorIA.ultimoModeloOk());
        corpo.put("ultimoErro", provedorIA.ultimoErro());
        corpo.put("modo", temChave ? "ia" : "regras (sem chave)");
        return ResponseEntity.ok(corpo);
    }

    @PostMapping("/redacao")
    @Operation(summary = "Reescrever uma necessidade da ONG (publico)")
    public ResponseEntity<?> redacao(@Valid @RequestBody RedacaoRequestDTO req) {
        if (rateLimitService.excedeuSolicitacoes("redacao", maxRedacao)) {
            return RateLimitService.resposta429();
        }
        // Regra: pelo menos rascunho OU titulo deve vir preenchido.
        boolean semTitulo = req.getTitulo() == null || req.getTitulo().isBlank();
        boolean semRascunho = req.getRascunho() == null || req.getRascunho().isBlank();
        if (semTitulo && semRascunho) {
            return ResponseEntity.badRequest().body(
                    java.util.Map.of("erro", "Informe um título ou um rascunho."));
        }
        return ResponseEntity.ok(redacaoService.redigir(req));
    }

    @PostMapping("/resumo-impacto")
    @Operation(summary = "Resumir o impacto de uma ONG para o doador (publico)")
    public ResponseEntity<?> resumoImpacto(@Valid @RequestBody ResumoImpactoRequestDTO req) {
        if (rateLimitService.excedeuSolicitacoes("resumo", maxResumo)) {
            return RateLimitService.resposta429();
        }
        return ResponseEntity.ok(resumoImpactoService.resumir(req.getOngId()));
    }

    @PostMapping("/sobre-ong")
    @Operation(summary = "Escrever/refinar o 'Sobre' institucional da ONG, com loop de ajuste (publico)")
    public ResponseEntity<?> sobreOng(@Valid @RequestBody SobreOngRequestDTO req) {
        if (rateLimitService.excedeuSolicitacoes("sobre", maxSobre)) {
            return RateLimitService.resposta429();
        }
        return ResponseEntity.ok(sobreOngService.gerar(req));
    }
}
