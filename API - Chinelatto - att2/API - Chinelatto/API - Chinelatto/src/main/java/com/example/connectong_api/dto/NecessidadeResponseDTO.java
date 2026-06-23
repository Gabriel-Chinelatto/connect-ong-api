package com.example.connectong_api.dto;

import java.time.LocalDateTime;

/**
 * Dados de saida de uma necessidade (inclui id e nome da ONG dona).
 */
public class NecessidadeResponseDTO {

    private Long id;
    private String titulo;
    private String descricao;
    private String categoria;
    private Boolean urgente;
    private String status;
    private LocalDateTime dataCriacao;
    private Long ongId;
    private String ongNome;

    public NecessidadeResponseDTO(
            Long id,
            String titulo,
            String descricao,
            String categoria,
            Boolean urgente,
            String status,
            LocalDateTime dataCriacao,
            Long ongId,
            String ongNome
    ) {
        this.id = id;
        this.titulo = titulo;
        this.descricao = descricao;
        this.categoria = categoria;
        this.urgente = urgente;
        this.status = status;
        this.dataCriacao = dataCriacao;
        this.ongId = ongId;
        this.ongNome = ongNome;
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public String getCategoria() { return categoria; }
    public Boolean getUrgente() { return urgente; }
    public String getStatus() { return status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public Long getOngId() { return ongId; }
    public String getOngNome() { return ongNome; }
}
