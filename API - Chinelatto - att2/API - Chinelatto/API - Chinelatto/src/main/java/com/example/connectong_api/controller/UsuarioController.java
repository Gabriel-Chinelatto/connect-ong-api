package com.example.connectong_api.controller;

import com.example.connectong_api.dto.CadastroUsuarioDTO;
import com.example.connectong_api.dto.LoginRequestDTO;
import com.example.connectong_api.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/login")
    @Operation(summary = "Autenticar usuário por email e senha")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequestDTO credenciais
    ) {

        return usuarioService.login(credenciais);
    }
}
