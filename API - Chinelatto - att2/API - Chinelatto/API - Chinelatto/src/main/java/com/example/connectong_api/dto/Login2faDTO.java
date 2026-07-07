package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Segundo passo do login com verificacao em duas etapas: o e-mail da conta e o
 * codigo de 6 digitos recebido (POST /auth/login-2fa).
 */
public class Login2faDTO {

    @NotBlank(message = "Informe o e-mail")
    private String email;

    @NotBlank(message = "Informe o código")
    private String codigo;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
}
