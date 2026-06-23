package com.example.connectong_api.dto;

/**
 * Dados de entrada para uma ONG publicar uma necessidade.
 */
public class NecessidadeRequestDTO {

    private String titulo;
    private String descricao;
    private String categoria;
    private Boolean urgente;
    private Long ongId;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Boolean getUrgente() { return urgente; }
    public void setUrgente(Boolean urgente) { this.urgente = urgente; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }
}
