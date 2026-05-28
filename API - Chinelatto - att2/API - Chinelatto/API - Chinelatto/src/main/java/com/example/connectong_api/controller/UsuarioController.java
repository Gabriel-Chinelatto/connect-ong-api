package com.example.connectong_api.controller;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.service.UsuarioService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    // =========================
    // CADASTRO
    // =========================
    @PostMapping
    public ResponseEntity<?> cadastrar(
            @RequestBody Usuario usuario
    ) {

        return usuarioService.cadastrar(usuario);
    }

    // =========================
    // LOGIN
    // =========================
    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Usuario usuario
    ) {

        return usuarioService.login(usuario);
    }
}