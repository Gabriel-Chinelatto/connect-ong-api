package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciais de login. Substitui o recebimento da entidade Usuario crua.
 */
public class LoginRequestDTO {

    @NotBlank(message = "Informe o email")
    private String email;

    @NotBlank(message = "Informe a senha")
    private String senha;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
}
