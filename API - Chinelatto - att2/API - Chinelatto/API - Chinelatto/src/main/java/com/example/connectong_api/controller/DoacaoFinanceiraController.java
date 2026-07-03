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

/**
 * Recurso REST /doacoes-financeiras: doacao em dinheiro via PIX simulado (sem gateway real).
 * Exige autenticacao (token JWT) e ownership: doar e listar o proprio historico exige ser o
 * doador autenticado; listar por ongId exige ser a propria ONG -> violacao retorna 403.
 */
@RestController
@RequestMapping("/doacoes-financeiras")
@Tag(name = "Doações Financeiras (PIX)",
        description = "Doação financeira simulada via PIX (sem gateway real)")
public class DoacaoFinanceiraController {

    @Autowired
    private DoacaoFinanceiraService service;

    @Autowired
    private SecurityUtils security;

    @PostMapping("/gerar-codigo")
    @Operation(summary = "Fase 1 do PIX simulado: gera o código \"copia e cola\" com o valor embutido (STATELESS, nada é persistido)")
    public ResponseEntity<?> gerarCodigo(@RequestBody java.util.Map<String, Object> body) {
        // Basta estar autenticado; nada e persistido nesta fase.
        Double valor = null;
        Object bruto = body != null ? body.get("valor") : null;
        if (bruto instanceof Number numero) {
            valor = numero.doubleValue();
        }
        return service.gerarCodigo(valor);
    }

    @PostMapping
    @Operation(summary = "Fazer uma doação financeira (PIX simulado) e gerar o comprovante; aceita codigoPix da fase 1 e campanhaId opcional")
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
