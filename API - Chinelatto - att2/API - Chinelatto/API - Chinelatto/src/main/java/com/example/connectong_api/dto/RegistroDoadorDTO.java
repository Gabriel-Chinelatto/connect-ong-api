package com.example.connectong_api.dto;

import com.example.connectong_api.validacao.*;
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
    @NomeProprio(message = "Nome inválido: use apenas letras (sem números ou símbolos)")
    private String nome;

    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Email inválido")
    @Size(max = 150, message = "O email é muito longo")
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    @SenhaForte
    private String senha;

    @Size(max = 20, message = "Telefone muito longo")
    @Telefone
    private String telefone;

    @Size(max = 100, message = "Cidade muito longa")
    @NomeProprio(message = "Cidade inválida: use apenas letras")
    private String cidade;

    @Size(max = 50, message = "Estado muito longo")
    @Uf
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

    // LGPD (F-04): o cadastro so e aceito com o aceite explicito dos Termos de
    // Uso e da Politica de Privacidade; o aceite e gravado na tabela consentimento.
    @jakarta.validation.constraints.NotNull(message = "É preciso aceitar os Termos de Uso e a Política de Privacidade")
    @jakarta.validation.constraints.AssertTrue(message = "É preciso aceitar os Termos de Uso e a Política de Privacidade")
    private Boolean aceiteTermos;

    public Boolean getAceiteTermos() { return aceiteTermos; }
    public void setAceiteTermos(Boolean aceiteTermos) { this.aceiteTermos = aceiteTermos; }
}
