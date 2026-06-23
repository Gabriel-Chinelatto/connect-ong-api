package com.example.connectong_api.dto;

/**
 * Dados de entrada para um doador demonstrar interesse em uma necessidade.
 */
public class InteresseRequestDTO {

    private Long necessidadeId;
    private Long doadorId;

    public Long getNecessidadeId() { return necessidadeId; }
    public void setNecessidadeId(Long necessidadeId) { this.necessidadeId = necessidadeId; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }
}
