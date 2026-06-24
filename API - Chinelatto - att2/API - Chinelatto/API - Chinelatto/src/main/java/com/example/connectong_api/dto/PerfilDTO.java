package com.example.connectong_api.dto;

/**
 * Dados de perfil do usuario (entrada e saida). Nunca inclui a senha.
 */
public class PerfilDTO {

    private Long id;
    private String nome;
    private String email;
    private String telefone;
    private String cidade;
    private String estado;
    private String bio;
    private String fotoUrl;
    private String tipo;
    private Long ongId;

    public PerfilDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }
}
