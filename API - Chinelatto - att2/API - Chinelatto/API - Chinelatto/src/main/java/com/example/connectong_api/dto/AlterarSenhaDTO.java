package com.example.connectong_api.dto;

import com.example.connectong_api.validacao.*;
import jakarta.validation.constraints.Size;

/**
 * Dados para alterar a senha do usuario.
 */
public class AlterarSenhaDTO {

    private String senhaAtual;

    // Politica de senha padronizada: minimo de 6 caracteres (igual ao cadastro).
    @SenhaForte
    private String novaSenha;

    public String getSenhaAtual() { return senhaAtual; }
    public void setSenhaAtual(String senhaAtual) { this.senhaAtual = senhaAtual; }

    public String getNovaSenha() { return novaSenha; }
    public void setNovaSenha(String novaSenha) { this.novaSenha = novaSenha; }
}
