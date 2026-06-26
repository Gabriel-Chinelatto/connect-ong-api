package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para uma ONG publicar uma necessidade.
 */
public class NecessidadeRequestDTO {

    @NotBlank(message = "O titulo e obrigatorio")
    @Size(min = 3, max = 150, message = "O titulo deve ter entre 3 e 150 caracteres")
    private String titulo;

    @NotBlank(message = "A descricao e obrigatoria")
    @Size(max = 2000, message = "A descricao deve ter no maximo 2000 caracteres")
    private String descricao;

    @Size(max = 60, message = "Categoria muito longa")
    private String categoria;

    private Boolean urgente;

    @NotNull(message = "O ongId e obrigatorio")
    @Positive(message = "O ongId deve ser um numero positivo")
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
