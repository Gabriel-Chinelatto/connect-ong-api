package com.example.connectong_api.controller;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.service.UsuarioService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "*")
@Tag(name = "Usuários", description = "Cadastro e login de usuários (doador e ONG)")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @PostMapping
    @Operation(summary = "Cadastrar um novo usuário (senha é criptografada)")
    public ResponseEntity<?> cadastrar(
            @RequestBody Usuario usuario
    ) {

        return usuarioService.cadastrar(usuario);
    }

    @PostMapping("/login")
    @Operation(summary = "Autenticar usuário por email e senha")
    public ResponseEntity<?> login(
            @RequestBody Usuario usuario
    ) {

        return usuarioService.login(usuario);
    }
}