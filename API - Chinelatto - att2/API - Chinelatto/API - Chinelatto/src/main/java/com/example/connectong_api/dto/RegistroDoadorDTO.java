package com.example.connectong_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados aceitos no cadastro publico de DOADOR (usado pelo app mobile).
 * O tipo NAO vem do cliente: o service fixa "DOADOR", entao ninguem consegue
 * se registrar como ONG por esta rota (evita escalonamento de privilegio).
 * Telefone, cidade e estado sao opcionais e vao direto para o perfil.
 */
public class RegistroDoadorDTO {

    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    private String nome;

    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 150, message = "O email é muito longo")
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 6, max = 100, message = "A senha deve ter ao menos 6 caracteres")
    private String senha;

    @Size(max = 20, message = "Telefone muito longo")
    private String telefone;

    @Size(max = 100, message = "Cidade muito longa")
    private String cidade;

    @Size(max = 50, message = "Estado muito longo")
    private String estado;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
