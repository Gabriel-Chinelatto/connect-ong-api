package com.example.connectong_api.dto;

/**
 * Dados para alterar a senha do usuario.
 */
public class AlterarSenhaDTO {

    private String senhaAtual;
    private String novaSenha;

    public String getSenhaAtual() { return senhaAtual; }
    public void setSenhaAtual(String senhaAtual) { this.senhaAtual = senhaAtual; }

    public String getNovaSenha() { return novaSenha; }
    public void setNovaSenha(String novaSenha) { this.novaSenha = novaSenha; }
}
