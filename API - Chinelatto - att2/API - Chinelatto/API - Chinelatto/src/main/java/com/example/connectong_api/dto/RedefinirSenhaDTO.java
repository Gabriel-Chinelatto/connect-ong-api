package com.example.connectong_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /auth/redefinir-senha. Contrato fixo com os frontends:
 * {"email","codigo","novaSenha"} — sucesso 200; qualquer falha de codigo
 * vira 400 generico ("Código inválido ou expirado."), sem revelar o motivo.
 */
public class RedefinirSenhaDTO {

    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 150, message = "O email é muito longo")
    private String email;

    @NotBlank(message = "O código é obrigatório")
    @Size(max = 10, message = "Código inválido ou expirado.")
    private String codigo;

    @NotBlank(message = "A nova senha é obrigatória")
    @Size(min = 6, max = 100, message = "A senha deve ter ao menos 6 caracteres")
    private String novaSenha;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNovaSenha() { return novaSenha; }
    public void setNovaSenha(String novaSenha) { this.novaSenha = novaSenha; }
}
