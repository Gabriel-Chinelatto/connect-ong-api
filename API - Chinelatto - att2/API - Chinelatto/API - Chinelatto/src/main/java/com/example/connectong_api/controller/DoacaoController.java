package com.example.connectong_api.controller;

import com.example.connectong_api.model.Doacao;
import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.service.DoacaoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /doacoes: catalogo de itens de doacao. Exige autenticacao (JWT).
 *
 * - GET /doacoes         -> catalogo global (usado pelo painel da ONG no desktop).
 * - GET /doacoes/minhas  -> so os itens do proprio doador (token) — "Minhas Doacoes".
 * O dono de cada item vem do token no cadastro (nunca do cliente); editar/excluir
 * exige ser o dono (itens legados sem dono seguem abertos).
 */
@RestController
@RequestMapping("/doacoes")
@Tag(name = "Doações", description = "Cadastro e gerenciamento de doações")
public class DoacaoController {

    @Autowired
    private DoacaoService service;

    @Autowired
    private SecurityUtils security;

    @GetMapping
    @Operation(summary = "Listar o catálogo global de doações")
    public ResponseEntity<?> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @GetMapping("/minhas")
    @Operation(summary = "Listar as doações do próprio doador autenticado")
    public ResponseEntity<?> minhas() {
        return ResponseEntity.ok(service.listarPorDoador(security.usuarioId()));
    }

    @PostMapping
    @Operation(summary = "Cadastrar uma nova doação (dono = usuário autenticado)")
    public ResponseEntity<?> criar(
            @RequestBody Doacao doacao
    ) {
        return service.criar(doacao, security.usuarioId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar uma doação (apenas o dono)")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @RequestBody Doacao doacao
    ) {
        return service.atualizar(id, doacao, security.usuarioId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir uma doação (apenas o dono)")
    public ResponseEntity<?> deletar(
            @PathVariable Long id
    ) {
        return service.deletar(id, security.usuarioId());
    }
}