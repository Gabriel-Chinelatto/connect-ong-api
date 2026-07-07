package com.example.connectong_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Dados para alterar o e-mail da propria conta (PUT /usuarios/{id}/email).
 * Exige a senha atual (confirmacao de identidade); o novo e-mail passa por
 * validacao de formato (@Email) e, no service, por checagem de unicidade.
 */
public class AlterarEmailDTO {

    @NotBlank(message = "Informe o novo e-mail")
    @Email(message = "E-mail invalido")
    private String novoEmail;

    @NotBlank(message = "Informe a senha")
    private String senha;

    public String getNovoEmail() { return novoEmail; }
    public void setNovoEmail(String novoEmail) { this.novoEmail = novoEmail; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
}
