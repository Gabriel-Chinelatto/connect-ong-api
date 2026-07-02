package com.example.connectong_api.controller;

import com.example.connectong_api.dto.CadastroUsuarioDTO;
import com.example.connectong_api.dto.LoginRequestDTO;
import com.example.connectong_api.dto.RegistroDoadorDTO;
import com.example.connectong_api.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /usuarios: cadastro e login de usuarios (doador e ONG).
 * Endpoints publicos (nao exigem token): o cadastro grava a senha criptografada e o login
 * autentica por email/senha; ambos retornam accessToken + refreshToken para as demais chamadas.
 */
@RestController
@RequestMapping("/usuarios")
@Tag(name = "Usuários", description = "Cadastro e login de usuários (doador e ONG)")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping
    @Operation(summary = "Cadastrar um novo usuário (senha é criptografada)")
    public ResponseEntity<?> cadastrar(
            @Valid @RequestBody CadastroUsuarioDTO dados
    ) {

        return usuarioService.cadastrar(dados);
    }

    @PostMapping("/registro")
    @Operation(summary = "Cadastro público de doador (app mobile) — tipo fixo DOADOR")
    public ResponseEntity<?> registrarDoador(
            @Valid @RequestBody RegistroDoadorDTO dados
    ) {

        return usuarioService.registrarDoador(dados);
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar usuário por email e senha")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequestDTO credenciais
    ) {

        return usuarioService.login(credenciais);
    }
}
