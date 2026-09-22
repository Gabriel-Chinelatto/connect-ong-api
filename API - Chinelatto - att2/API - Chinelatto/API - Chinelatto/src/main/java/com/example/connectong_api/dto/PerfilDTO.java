package com.example.connectong_api.dto;

import com.example.connectong_api.validacao.*;
import jakarta.validation.constraints.Size;

/**
 * Dados de perfil do usuario (entrada e saida). Nunca inclui a senha.
 */
public class PerfilDTO {

    private Long id;
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    @TextoLegivel(message = "Nome inválido: use letras e sem os caracteres < e >")
    private String nome;
    private String email;
    @Size(max = 20, message = "Telefone muito longo")
    @Telefone
    private String telefone;
    @Size(max = 100, message = "Cidade muito longa")
    @NomeProprio(message = "Cidade inválida: use apenas letras")
    private String cidade;
    @Size(max = 50, message = "Estado muito longo")
    @Uf
    private String estado;
    @Size(max = 500, message = "A bio deve ter no máximo 500 caracteres")
    @TextoLegivel(message = "Bio inválida: escreva com letras e sem os caracteres < e >")
    private String bio;
    @Size(max = 500, message = "URL da foto muito longa")
    private String fotoUrl;

    // Foto embutida em base64. Limita o tamanho para evitar payloads gigantes
    // (DoS de memoria/banco). ~3 MB de string cobre uma foto comprimida com folga.
    @Size(max = 3_000_000, message = "Imagem muito grande")
    private String fotoBase64;
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

    public String getFotoBase64() { return fotoBase64; }
    public void setFotoBase64(String fotoBase64) { this.fotoBase64 = fotoBase64; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }
}
