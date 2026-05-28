package com.example.connectong_api.dto;

public class ProjetoResponseDTO {

    private Long id;

    private String titulo;

    private String descricao;

    private Double metaValor;

    private Long ongId;

    public ProjetoResponseDTO(
            Long id,
            String titulo,
            String descricao,
            Double metaValor,
            Long ongId
    ) {

        this.id = id;
        this.titulo = titulo;
        this.descricao = descricao;
        this.metaValor = metaValor;
        this.ongId = ongId;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDescricao() {
        return descricao;
    }

    public Double getMetaValor() {
        return metaValor;
    }

    public Long getOngId() {
        return ongId;
    }
}