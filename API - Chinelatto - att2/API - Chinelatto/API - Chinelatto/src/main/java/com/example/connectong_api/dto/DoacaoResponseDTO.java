package com.example.connectong_api.dto;

public class DoacaoResponseDTO {

    private Long id;

    private String nome;

    private String descricao;

    private Integer quantidade;

    private String categoria;

    private String tipo;

    private Boolean urgente;

    private Boolean novo;

    public DoacaoResponseDTO(
            Long id,
            String nome,
            String descricao,
            Integer quantidade,
            String categoria,
            String tipo,
            Boolean urgente,
            Boolean novo
    ) {

        this.id = id;
        this.nome = nome;
        this.descricao = descricao;
        this.quantidade = quantidade;
        this.categoria = categoria;
        this.tipo = tipo;
        this.urgente = urgente;
        this.novo = novo;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getTipo() {
        return tipo;
    }

    public Boolean getUrgente() {
        return urgente;
    }

    public Boolean getNovo() {
        return novo;
    }
}