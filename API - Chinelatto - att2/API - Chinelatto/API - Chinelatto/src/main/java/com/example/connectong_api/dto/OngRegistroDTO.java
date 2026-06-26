package com.example.connectong_api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados para o cadastro de uma ONG: cria o perfil (Ong) E a conta de login
 * (Usuario tipo ONG) ja vinculados.
 */
public class OngRegistroDTO {

    @NotBlank(message = "O nome da ONG e obrigatorio")
    @Size(min = 2, max = 120, message = "O nome deve ter entre 2 e 120 caracteres")
    private String nome;

    @NotBlank(message = "O e-mail e obrigatorio")
    @Email(message = "E-mail invalido")
    @Size(max = 150, message = "O e-mail e muito longo")
    private String email;

    @Size(max = 20, message = "Telefone muito longo")
    private String telefone;

    @Size(max = 100, message = "Cidade muito longa")
    private String cidade;

    @Size(max = 1000, message = "A descricao deve ter no maximo 1000 caracteres")
    private String descricao;

    @Size(max = 20, message = "CNPJ muito longo")
    private String cnpj;

    @NotBlank(message = "A senha e obrigatoria")
    @Size(min = 4, max = 100, message = "A senha deve ter no minimo 4 caracteres")
    private String senha;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
}
