package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Notificacao para um usuario (doador ou ONG).
 */
@Entity
@Table(name = "notificacao")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId; // destinatario

    private String titulo;

    @Column(length = 400)
    private String mensagem;

    private String tipo; // MATCH, MENSAGEM, PRESTACAO, etc.

    private Boolean lida = false;

    private LocalDateTime dataCriacao;

    public Notificacao() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
        if (this.lida == null) this.lida = false;
    }

    public Long getId() { return id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Boolean getLida() { return lida != null && lida; }
    public void setLida(Boolean lida) { this.lida = lida; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
