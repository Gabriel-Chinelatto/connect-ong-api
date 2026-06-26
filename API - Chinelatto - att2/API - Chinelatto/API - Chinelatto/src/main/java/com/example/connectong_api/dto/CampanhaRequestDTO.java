package com.example.connectong_api.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Dados de entrada para uma ONG criar/editar uma campanha.
 */
public class CampanhaRequestDTO {

    @NotBlank(message = "O titulo e obrigatorio")
    @Size(min = 3, max = 150, message = "O titulo deve ter entre 3 e 150 caracteres")
    private String titulo;

    @Size(max = 255, message = "A descricao deve ter no maximo 255 caracteres")
    private String descricao;

    @NotNull(message = "A meta e obrigatoria")
    @DecimalMin(value = "1.0", message = "A meta deve ser maior que zero")
    private Double metaValor;

    @Size(max = 60, message = "Categoria muito longa")
    private String categoria;

    private LocalDate dataInicio;
    private LocalDate dataFim;

    private Boolean destaque;

    @NotNull(message = "O ongId e obrigatorio")
    @Positive(message = "O ongId deve ser um numero positivo")
    private Long ongId;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Double getMetaValor() { return metaValor; }
    public void setMetaValor(Double metaValor) { this.metaValor = metaValor; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public LocalDate getDataInicio() { return dataInicio; }
    public void setDataInicio(LocalDate dataInicio) { this.dataInicio = dataInicio; }

    public LocalDate getDataFim() { return dataFim; }
    public void setDataFim(LocalDate dataFim) { this.dataFim = dataFim; }

    public Boolean getDestaque() { return destaque; }
    public void setDestaque(Boolean destaque) { this.destaque = destaque; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }
}
