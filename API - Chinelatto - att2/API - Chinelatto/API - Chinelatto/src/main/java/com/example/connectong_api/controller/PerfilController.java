package com.example.connectong_api.controller;

import com.example.connectong_api.dto.AlterarEmailDTO;
import com.example.connectong_api.dto.AlterarSenhaDTO;
import com.example.connectong_api.dto.PerfilDTO;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.service.PerfilService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recurso REST /usuarios/{id}: perfil do usuario, troca de senha e preferencias/configuracoes.
 * Exige autenticacao (token JWT) e ownership: cada usuario so le/altera o proprio perfil,
 * senha e preferencias -> violacao retorna 403.
 */
@RestController
@RequestMapping("/usuarios/{id}")
@Tag(name = "Perfil e Configurações",
        description = "Perfil do usuário, troca de senha e preferências")
public class PerfilController {

    @Autowired
    private PerfilService service;

    @Autowired
    private SecurityUtils security;

    @Autowired
    private com.example.connectong_api.service.LgpdService lgpdService;

    @GetMapping("/meus-dados")
    @Operation(summary = "LGPD: baixar todos os dados pessoais guardados sobre o usuário (acesso e portabilidade)")
    public ResponseEntity<?> meusDados(@PathVariable Long id) {
        security.exigirUsuario(id);
        return lgpdService.exportar(id)
                .<ResponseEntity<?>>map(dados -> ResponseEntity.ok()
                        .header("Content-Disposition", "attachment; filename=\"connect-ong-meus-dados.json\"")
                        .body(dados))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/consentimentos")
    @Operation(summary = "LGPD: histórico de aceites dos Termos de Uso e da Política de Privacidade")
    public ResponseEntity<?> consentimentos(@PathVariable Long id) {
        security.exigirUsuario(id);
        return ResponseEntity.ok(lgpdService.consentimentos(id));
    }

    @GetMapping("/perfil")
    @Operation(summary = "Obter o perfil do usuário")
    public ResponseEntity<?> obterPerfil(@PathVariable Long id) {
        security.exigirUsuario(id);
        return service.obterPerfil(id);
    }

    @PutMapping("/perfil")
    @Operation(summary = "Atualizar o perfil do usuário")
    public ResponseEntity<?> atualizarPerfil(
            @PathVariable Long id,
            @Valid @RequestBody PerfilDTO dto
    ) {
        security.exigirUsuario(id);
        return service.atualizarPerfil(id, dto);
    }

    @PutMapping("/senha")
    @Operation(summary = "Alterar a senha do usuário")
    public ResponseEntity<?> alterarSenha(
            @PathVariable Long id,
            @Valid @RequestBody AlterarSenhaDTO dto
    ) {
        security.exigirUsuario(id);
        return service.alterarSenha(id, dto);
    }

    @PutMapping("/email")
    @Operation(summary = "Alterar o e-mail da própria conta (exige a senha atual)")
    public ResponseEntity<?> alterarEmail(
            @PathVariable Long id,
            @Valid @RequestBody AlterarEmailDTO dto
    ) {
        security.exigirUsuario(id);
        return service.alterarEmail(id, dto);
    }

    @DeleteMapping
    @Operation(summary = "Excluir a própria conta: deixa de logar e os dados pessoais são anonimizados (LGPD)")
    public ResponseEntity<?> excluirConta(@PathVariable Long id) {
        security.exigirUsuario(id);
        return service.excluirConta(id);
    }

    @GetMapping("/preferencias")
    @Operation(summary = "Obter as preferências/configurações do usuário")
    public ResponseEntity<?> obterPreferencias(@PathVariable Long id) {
        security.exigirUsuario(id);
        return service.obterPreferencias(id);
    }

    @PutMapping("/preferencias")
    @Operation(summary = "Atualizar as preferências/configurações do usuário")
    public ResponseEntity<?> atualizarPreferencias(
            @PathVariable Long id,
            @RequestBody Preferencia dados
    ) {
        security.exigirUsuario(id);
        return service.atualizarPreferencias(id, dados);
    }
}
