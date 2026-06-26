package com.example.connectong_api.controller;

import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.service.FavoritoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Recurso REST /favoritos: o doador favorita ONGs e campanhas para acompanhar.
 * Permite listar, consultar ids favoritados, favoritar (idempotente) e desfavoritar.
 * Exige autenticacao (token JWT) e ownership: so o proprio usuario manipula seus favoritos -> 403.
 */
@RestController
@RequestMapping("/favoritos")
@Tag(name = "Favoritos", description = "Doador favorita ONGs e campanhas para acompanhar")
public class FavoritoController {

    @Autowired
    private FavoritoService service;

    @Autowired
    private SecurityUtils security;

    @GetMapping
    @Operation(summary = "Listar favoritos de um doador (com nome do alvo resolvido)")
    public ResponseEntity<?> listar(@RequestParam Long usuarioId) {
        security.exigirUsuario(usuarioId);
        return ResponseEntity.ok(service.listar(usuarioId));
    }

    @GetMapping("/ids")
    @Operation(summary = "Ids dos alvos favoritados de um tipo (para marcar o coracao no app)")
    public ResponseEntity<?> ids(@RequestParam Long usuarioId,
                                 @RequestParam(defaultValue = "ONG") String tipo) {
        security.exigirUsuario(usuarioId);
        return ResponseEntity.ok(service.ids(usuarioId, tipo));
    }

    @PostMapping
    @Operation(summary = "Favoritar (idempotente) — body: usuarioId, tipo (ONG|CAMPANHA), alvoId")
    public ResponseEntity<?> adicionar(@RequestBody Map<String, Object> body) {
        Long usuarioId = body.get("usuarioId") != null
                ? Long.valueOf(body.get("usuarioId").toString()) : null;
        Long alvoId = body.get("alvoId") != null
                ? Long.valueOf(body.get("alvoId").toString()) : null;
        String tipo = body.get("tipo") != null ? body.get("tipo").toString() : null;
        security.exigirUsuario(usuarioId);
        return service.adicionar(usuarioId, tipo, alvoId);
    }

    @DeleteMapping
    @Operation(summary = "Desfavoritar — params: usuarioId, tipo, alvoId")
    public ResponseEntity<?> remover(@RequestParam Long usuarioId,
                                     @RequestParam String tipo,
                                     @RequestParam Long alvoId) {
        security.exigirUsuario(usuarioId);
        return service.remover(usuarioId, tipo, alvoId);
    }
}
