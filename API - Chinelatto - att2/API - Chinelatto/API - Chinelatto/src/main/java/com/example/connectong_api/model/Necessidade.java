package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Uma necessidade publicada por uma ONG (o que ela precisa receber).
 * Os doadores podem demonstrar interesse nela (ver entidade Interesse).
 */
@Entity
@Table(name = "necessidade")
public class Necessidade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "ong_id")
    private Ong ong;

    private String titulo;
    private String descricao;
    private String categoria;
    private Boolean urgente;

    // ABERTA (recebendo interesses) ou ENCERRADA (ja atendida)
    private String status;

    private LocalDateTime dataCriacao;

    public Necessidade() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
        if (this.status == null || this.status.isBlank()) {
            this.status = "ABERTA";
        }
        if (this.urgente == null) {
            this.urgente = false;
        }
    }

    public Long getId() { return id; }

    public Ong getOng() { return ong; }
    public void setOng(Ong ong) { this.ong = ong; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Boolean getUrgente() { return urgente; }
    public void setUrgente(Boolean urgente) { this.urgente = urgente; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
