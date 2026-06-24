package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Prestacao de contas: a ONG mostra ao doador o resultado da doacao
 * (relato + foto), dentro de um match aceito.
 */
@Entity
@Table(name = "prestacao")
public class Prestacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "interesse_id")
    private Interesse interesse;

    private String titulo;

    @Column(length = 1000)
    private String descricao;

    @Column(length = 500)
    private String fotoUrl;

    private LocalDateTime dataCriacao;

    public Prestacao() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Interesse getInteresse() { return interesse; }
    public void setInteresse(Interesse interesse) { this.interesse = interesse; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
