package com.example.connectong_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /auth/esqueci-senha. Contrato fixo com os frontends:
 * {"email":"..."} — a resposta e SEMPRE 200 generica (anti-enumeracao).
 */
public class EsqueciSenhaDTO {

    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 150, message = "O email é muito longo")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
