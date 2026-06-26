package com.example.connectong_api.dto;

public class DoacaoFinanceiraRequestDTO {

    private Long ongId;
    private Long doadorId;
    private Double valor;

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }
}
