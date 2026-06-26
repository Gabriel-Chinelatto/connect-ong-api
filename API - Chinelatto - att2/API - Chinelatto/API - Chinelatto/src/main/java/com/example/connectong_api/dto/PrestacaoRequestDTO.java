package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para a ONG publicar uma prestacao de contas num match.
 */
public class PrestacaoRequestDTO {

    @NotNull(message = "O interesseId e obrigatorio")
    @Positive(message = "O interesseId deve ser um numero positivo")
    private Long interesseId;

    @NotBlank(message = "O titulo e obrigatorio")
    @Size(max = 150, message = "O titulo deve ter no maximo 150 caracteres")
    private String titulo;

    @NotBlank(message = "A descricao e obrigatoria")
    @Size(max = 2000, message = "A descricao deve ter no maximo 2000 caracteres")
    private String descricao;

    @Size(max = 500, message = "A URL da foto e muito longa")
    private String fotoUrl;

    public Long getInteresseId() { return interesseId; }
    public void setInteresseId(Long interesseId) { this.interesseId = interesseId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }
}
