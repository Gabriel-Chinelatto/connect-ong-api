package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * Campanha de arrecadacao de uma ONG (meta + valor arrecadado + progresso).
 *
 * Reaproveita a antiga tabela `projeto` (renomeada para `campanha` via
 * migration Liquibase) — titulo/descricao/meta_valor/ong_id ja existiam.
 */
@Entity
@Table(name = "campanha")
public class Campanha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private String descricao;

    private Double metaValor;
    private Double valorArrecadado = 0.0;

    private String categoria;

    private LocalDate dataInicio;
    private LocalDate dataFim;

    private Boolean encerrada = false;
    private Boolean destaque = false;

    @ManyToOne
    @JoinColumn(name = "ong_id")
    private Ong ong;

    public Campanha() {}

    public Long getId() { return id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Double getMetaValor() { return metaValor; }
    public void setMetaValor(Double metaValor) { this.metaValor = metaValor; }

    public Double getValorArrecadado() {
        return valorArrecadado != null ? valorArrecadado : 0.0;
    }
    public void setValorArrecadado(Double valorArrecadado) { this.valorArrecadado = valorArrecadado; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }

    public Boolean getEncerrada() { return encerrada != null && encerrada; }
    public void setEncerrada(Boolean encerrada) { this.encerrada = encerrada; }

    public Boolean getDestaque() { return destaque != null && destaque; }
    public void setDestaque(Boolean destaque) { this.destaque = destaque; }

    public Ong getOng() { return ong; }
    public void setOng(Ong ong) { this.ong = ong; }
}
