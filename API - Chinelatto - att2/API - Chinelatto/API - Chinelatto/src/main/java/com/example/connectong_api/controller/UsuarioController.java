package com.example.connectong_api.controller;

import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    // ✅ CADASTRO
    @PostMapping
    public ResponseEntity<?> cadastrar(@RequestBody Usuario usuario) {

        // 🔴 valida campos obrigatórios
        if (usuario.getNome() == null || usuario.getNome().isEmpty() ||
                usuario.getEmail() == null || usuario.getEmail().isEmpty() ||
                usuario.getSenha() == null || usuario.getSenha().isEmpty() ||
                usuario.getTipo() == null || usuario.getTipo().isEmpty()) {

            return ResponseEntity
                    .badRequest()
                    .body("Preencha todos os campos");
        }

        // 🔴 valida email duplicado
        if (usuarioRepository.findByEmail(usuario.getEmail()).isPresent()) {
            return ResponseEntity
                    .badRequest()
                    .body("Email já cadastrado");
        }

        Usuario novo = usuarioRepository.save(usuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(novo);
    }

    // ✅ LOGIN (VERSÃO CORRETA)
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Usuario usuario) {

        // 🔴 valida apenas email e senha
        if (usuario.getEmail() == null || usuario.getEmail().isEmpty() ||
                usuario.getSenha() == null || usuario.getSenha().isEmpty()) {

            return ResponseEntity
                    .badRequest()
                    .body("Informe email e senha");
        }

        Optional<Usuario> usuarioBanco =
                usuarioRepository.findByEmail(usuario.getEmail());

        if (usuarioBanco.isPresent()) {

            if (usuarioBanco.get().getSenha().equals(usuario.getSenha())) {
                return ResponseEntity.ok(usuarioBanco.get());
            }

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body("Senha incorreta");
        }

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body("Usuário não encontrado");
    }
}