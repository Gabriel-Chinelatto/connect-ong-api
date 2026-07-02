package com.example.connectong_api.controller;

import com.example.connectong_api.util.Categorias;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Recurso REST /categorias: lista canonica de categorias usada por
 * necessidades, doacoes e campanhas. Endpoint PUBLICO (sem token),
 * para os apps montarem seus filtros/dropdowns.
 */
@RestController
@RequestMapping("/categorias")
@Tag(name = "Categorias", description = "Lista canonica de categorias (publica)")
public class CategoriaController {

    @GetMapping
    @Operation(summary = "Listar as categorias canonicas (publico)")
    public ResponseEntity<List<String>> listar() {
        return ResponseEntity.ok(Categorias.CANONICAS);
    }
}
