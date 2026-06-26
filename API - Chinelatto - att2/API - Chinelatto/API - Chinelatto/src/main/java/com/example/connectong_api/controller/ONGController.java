package com.example.connectong_api.controller;

import com.example.connectong_api.dto.OngRegistroDTO;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.service.ONGService;
import com.example.connectong_api.service.TransparenciaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ongs")
@CrossOrigin(origins = "*")
@Tag(name = "ONGs", description = "Cadastro, busca e gerenciamento de ONGs")
public class ONGController {

    @Autowired
    private ONGService service;

    @Autowired
    private TransparenciaService transparenciaService;

    @PostMapping("/registro")
    @Operation(summary = "Cadastrar uma ONG (cria o perfil + a conta de login juntos)")
    public ResponseEntity<?> registrar(
            @Valid @RequestBody OngRegistroDTO dto
    ) {
        return service.registrar(dto);
    }

    @PutMapping("/{id}/verificar")
    @Operation(summary = "Marcar uma ONG como verificada (selo de confiança)")
    public ResponseEntity<?> verificar(@PathVariable Long id) {
        return service.verificar(id);
    }

    @GetMapping
    @Operation(summary = "Listar ONGs (filtra por nome com o parametro 'nome')")
    public ResponseEntity<?> listar(
            @RequestParam(required = false)
            String nome
    ) {

        return ResponseEntity.ok(
                service.listar(nome)
        );
    }

    @GetMapping("/{id}/perfil-publico")
    @Operation(summary = "Perfil publico da ONG (dados + selo + avaliacoes + campanhas + necessidades + prestacoes)")
    public ResponseEntity<?> perfilPublico(@PathVariable Long id) {
        return service.perfilPublico(id);
    }

    @GetMapping("/{id}/transparencia")
    @Operation(summary = "Indice de transparencia da ONG (score 0-100 + nivel bronze/prata/ouro)")
    public ResponseEntity<?> transparencia(@PathVariable Long id) {
        return transparenciaService.transparencia(id);
    }

    @PostMapping
    @Operation(summary = "Cadastrar uma nova ONG")
    public ResponseEntity<?> criar(
            @RequestBody Ong ong
    ) {

        return service.criar(ong);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar uma ONG existente")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @RequestBody Ong ong
    ) {

        return service.atualizar(
                id,
                ong
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir uma ONG")
    public ResponseEntity<?> deletar(
            @PathVariable Long id
    ) {

        return service.deletar(id);
    }
}