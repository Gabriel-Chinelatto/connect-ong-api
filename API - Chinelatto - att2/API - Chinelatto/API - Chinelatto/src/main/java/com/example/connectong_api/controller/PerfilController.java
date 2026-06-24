package com.example.connectong_api.controller;

import com.example.connectong_api.dto.AlterarSenhaDTO;
import com.example.connectong_api.dto.PerfilDTO;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.service.PerfilService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios/{id}")
@CrossOrigin(origins = "*")
@Tag(name = "Perfil e Configurações",
        description = "Perfil do usuário, troca de senha e preferências")
public class PerfilController {

    @Autowired
    private PerfilService service;

    @GetMapping("/perfil")
    @Operation(summary = "Obter o perfil do usuário")
    public ResponseEntity<?> obterPerfil(@PathVariable Long id) {
        return service.obterPerfil(id);
    }

    @PutMapping("/perfil")
    @Operation(summary = "Atualizar o perfil do usuário")
    public ResponseEntity<?> atualizarPerfil(
            @PathVariable Long id,
            @RequestBody PerfilDTO dto
    ) {
        return service.atualizarPerfil(id, dto);
    }

    @PutMapping("/senha")
    @Operation(summary = "Alterar a senha do usuário")
    public ResponseEntity<?> alterarSenha(
            @PathVariable Long id,
            @RequestBody AlterarSenhaDTO dto
    ) {
        return service.alterarSenha(id, dto);
    }

    @GetMapping("/preferencias")
    @Operation(summary = "Obter as preferências/configurações do usuário")
    public ResponseEntity<?> obterPreferencias(@PathVariable Long id) {
        return service.obterPreferencias(id);
    }

    @PutMapping("/preferencias")
    @Operation(summary = "Atualizar as preferências/configurações do usuário")
    public ResponseEntity<?> atualizarPreferencias(
            @PathVariable Long id,
            @RequestBody Preferencia dados
    ) {
        return service.atualizarPreferencias(id, dados);
    }
}
