package com.example.connectong_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados aceitos no cadastro de usuario. Diferente de receber a entidade crua,
 * este DTO NAO expoe "id" nem "ongId" ao cliente, evitando mass assignment
 * (ex.: um doador tentar se cadastrar apontando para o ongId de outra ONG).
 * O "tipo" e limitado a DOADOR/ONG.
 */
public class CadastroUsuarioDTO {

    @NotBlank(message = "O nome é obrigatório")
    private String nome;

    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 6, message = "A senha deve ter ao menos 6 caracteres")
    private String senha;

    @NotBlank(message = "O tipo é obrigatório")
    @Pattern(regexp = "DOADOR|ONG", message = "Tipo deve ser DOADOR ou ONG")
    private String tipo;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
}
