package com.example.connectong_api.controller;

import com.example.connectong_api.service.DemoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * "Modo Feira": carrega dados demonstrativos para a apresentacao.
 */
@RestController
@RequestMapping("/demo")
@CrossOrigin(origins = "*")
@Tag(name = "Modo Feira", description = "Carrega dados demonstrativos (apresentacao)")
public class DemoController {

    @Autowired
    private DemoService demoService;

    @PostMapping("/seed")
    @Operation(summary = "Carrega dados demonstrativos (idempotente; senha padrao demo123)")
    public ResponseEntity<Map<String, Object>> seed() {
        return ResponseEntity.ok(demoService.carregar());
    }
}
