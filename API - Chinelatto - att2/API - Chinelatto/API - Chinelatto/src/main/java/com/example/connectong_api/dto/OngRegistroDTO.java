package com.example.connectong_api.dto;

/**
 * Dados para o cadastro de uma ONG: cria o perfil (Ong) E a conta de login
 * (Usuario tipo ONG) ja vinculados.
 */
public class OngRegistroDTO {

    private String nome;
    private String email;
    private String telefone;
    private String cidade;
    private String descricao;
    private String cnpj;
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
