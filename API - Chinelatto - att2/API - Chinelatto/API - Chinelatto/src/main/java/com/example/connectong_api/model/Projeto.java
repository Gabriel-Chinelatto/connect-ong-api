package com.example.connectong_api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "projeto")
public class Projeto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private String descricao;
    private Double metaValor;

    @ManyToOne
    @JoinColumn(name = "ong_id")
    private Ong ong;

    public Projeto() {}

    public Long getId() { return id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Double getMetaValor() { return metaValor; }
    public void setMetaValor(Double metaValor) { this.metaValor = metaValor; }

    public Ong getOng() { return ong; }
    public void setOng(Ong ong) { this.ong = ong; }
}