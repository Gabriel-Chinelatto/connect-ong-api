package com.example.connectong_api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Conta de acesso da plataforma. Representa tanto o DOADOR quanto o usuario
 * administrador de uma ONG, diferenciados pelo campo {@code tipo}.
 * Quando o usuario e de uma ONG, {@code ongId} aponta para o perfil em {@link Ong}
 * (fica null para doadores).
 * Dado sensivel: {@code senha} guarda o hash BCrypt e e WRITE_ONLY no JSON
 * (entra na desserializacao, nunca sai na resposta) para nao vazar o hash.
 */
@Entity
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String email;
    // A senha (hash BCrypt) so pode ENTRAR (desserializacao), nunca SAIR no JSON.
    // Evita vazar o hash caso a entidade seja serializada direta ou via associacao.
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String senha;
    private String tipo;

    // Quando o usuario e uma ONG, aponta para o perfil da ONG (tabela ong).
    // Fica null para doadores.
    private Long ongId;

    // ----- Dados de perfil -----
    private String telefone;
    private String cidade;
    private String estado;
    @Column(length = 300)
    private String bio;
    @Column(length = 500)
    private String fotoUrl;

    // Foto enviada da galeria, guardada como base64 (LONGTEXT). Alternativa ao
    // fotoUrl para quem escolhe uma imagem em vez de colar uma URL.
    @Column(name = "foto_base64", columnDefinition = "LONGTEXT")
    private String fotoBase64;

    // Presenca ("online" / "visto por ultimo"): momento da ultima atividade do
    // usuario no chat. Atualizado a cada batimento (poll do status). "Online" e
    // derivado no service (visto ha menos de X segundos).
    private LocalDateTime ultimoVisto;

    // Soft-delete: null = conta ativa; preenchida = conta "excluida" (nao loga e
    // some da UI, mas o historico e preservado, sem orfaos).
    private LocalDateTime dataExclusao;

    // GETTERS E SETTERS

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Long getOngId() {
        return ongId;
    }

    public void setOngId(Long ongId) {
        this.ongId = ongId;
    }

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

    public LocalDateTime getUltimoVisto() { return ultimoVisto; }
    public void setUltimoVisto(LocalDateTime ultimoVisto) { this.ultimoVisto = ultimoVisto; }

    public LocalDateTime getDataExclusao() { return dataExclusao; }
    public void setDataExclusao(LocalDateTime dataExclusao) { this.dataExclusao = dataExclusao; }
}