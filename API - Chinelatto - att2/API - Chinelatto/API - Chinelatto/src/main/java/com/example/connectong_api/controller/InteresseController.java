package com.example.connectong_api.controller;

import com.example.connectong_api.dto.InteresseRequestDTO;
import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.service.InteresseService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Recurso REST /interesses: hero feature do match. O doador demonstra interesse numa
 * necessidade e a ONG aceita (vira match) ou recusa; o match aceito e o que habilita o chat.
 * Exige autenticacao (token JWT) e ownership: doador so ve/cria os proprios interesses e a ONG
 * so os da propria ONG -> violacao retorna 403.
 */
@RestController
@RequestMapping("/interesses")
@Tag(name = "Interesses (Match)",
        description = "Doadores demonstram interesse; ONGs aceitam ou recusam")
public class InteresseController {

    @Autowired
    private InteresseService service;

    @Autowired
    private SecurityUtils security;

    @GetMapping
    @Operation(summary = "Listar interesses (filtra por doadorId ou ongId)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Long doadorId,
            @RequestParam(required = false) Long ongId
    ) {
        // SEGURANCA: e obrigatorio informar um filtro (doadorId OU ongId). Sem ele
        // o service cairia em findAll() e devolveria TODOS os matches da plataforma
        // (nomes de doadores, ONGs, necessidades) a qualquer autenticado — IDOR.
        if (doadorId == null && ongId == null) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Informe doadorId ou ongId para listar os interesses.");
            return ResponseEntity.badRequest().body(erro);
        }
        // O doador so lista os proprios interesses; a ONG so os da propria ONG.
        if (doadorId != null) {
            security.exigirUsuario(doadorId);
        } else {
            security.exigirOng(ongId);
        }
        return ResponseEntity.ok(service.listar(doadorId, ongId));
    }

    @PostMapping
    @Operation(summary = "Doador demonstra interesse em uma necessidade")
    public ResponseEntity<?> demonstrar(
            @Valid @RequestBody InteresseRequestDTO dto
    ) {
        // O interesse e do proprio doador autenticado.
        security.exigirUsuario(dto.getDoadorId());
        return service.demonstrarInteresse(dto);
    }

    @PutMapping("/{id}/aceitar")
    @Operation(summary = "ONG aceita o interesse (vira um match)")
    public ResponseEntity<?> aceitar(@PathVariable Long id) {
        // A checagem de dono (a ONG do interesse) e feita no service.
        return service.aceitar(id);
    }

    @PutMapping("/{id}/recusar")
    @Operation(summary = "ONG recusa o interesse")
    public ResponseEntity<?> recusar(@PathVariable Long id) {
        return service.recusar(id);
    }

    @PutMapping("/{id}/concluir")
    @Operation(summary = "ONG marca o match ACEITO como CONCLUÍDO (doação recebida); abre o prazo da prestação de contas")
    public ResponseEntity<?> concluir(@PathVariable Long id) {
        // A checagem de dono (a ONG do interesse) e feita no service.
        return service.concluir(id);
    }
}
