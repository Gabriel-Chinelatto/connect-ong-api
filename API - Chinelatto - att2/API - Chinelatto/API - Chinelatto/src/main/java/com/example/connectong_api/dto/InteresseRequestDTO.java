package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Dados de entrada para um doador demonstrar interesse em uma necessidade.
 */
public class InteresseRequestDTO {

    @NotNull(message = "O necessidadeId e obrigatorio")
    @Positive(message = "O necessidadeId deve ser um numero positivo")
    private Long necessidadeId;

    @NotNull(message = "O doadorId e obrigatorio")
    @Positive(message = "O doadorId deve ser um numero positivo")
    private Long doadorId;

    public Long getNecessidadeId() { return necessidadeId; }
    public void setNecessidadeId(Long necessidadeId) { this.necessidadeId = necessidadeId; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }
}
