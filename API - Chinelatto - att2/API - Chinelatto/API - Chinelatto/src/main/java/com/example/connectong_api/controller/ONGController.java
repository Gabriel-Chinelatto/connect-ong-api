package com.example.connectong_api.controller;

import com.example.connectong_api.dto.OngRegistroDTO;
import com.example.connectong_api.dto.OngUpdateDTO;
import com.example.connectong_api.service.ONGService;
import com.example.connectong_api.service.TransparenciaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /ongs: cadastro, busca e gerenciamento de ONGs (perfil + conta de login).
 * O registro e a leitura (listagem, perfil publico, indice de transparencia) tendem a ser publicos;
 * verificar/editar/excluir exigem autenticacao e ownership da propria ONG -> violacao retorna 403.
 */
@RestController
@RequestMapping("/ongs")
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
    @Operation(summary = "Listar ONGs. 'nome' busca por nome OU cidade; "
                       + "'pagina' e 'tamanho' carregam aos poucos (sem eles, vem tudo)")
    public ResponseEntity<?> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Integer pagina,
            @RequestParam(required = false) Integer tamanho
    ) {

        return ResponseEntity.ok(
                service.listar(nome, pagina, tamanho)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar uma ONG pelo id (perfil completo: capa, endereço e fotos do local)")
    public ResponseEntity<?> obterPorId(@PathVariable Long id) {
        return service.obterPorId(id);
    }

    @GetMapping("/{id}/perfil-publico")
    @Operation(summary = "Perfil publico da ONG (dados + selo + avaliacoes + campanhas + necessidades + prestacoes + capa/endereco/fotos + streak do ranking)")
    public ResponseEntity<?> perfilPublico(@PathVariable Long id) {
        return service.perfilPublico(id);
    }

    @GetMapping("/{id}/transparencia")
    @Operation(summary = "Indice de transparencia da ONG (score 0-100 + nivel bronze/prata/ouro)")
    public ResponseEntity<?> transparencia(@PathVariable Long id) {
        return transparenciaService.transparencia(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar uma ONG existente (aceita capaBase64, endereco e fotosLocal — a lista substitui as fotos atuais)")
    public ResponseEntity<?> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody OngUpdateDTO ong
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