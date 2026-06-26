package com.example.connectong_api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class DoacaoFinanceiraRequestDTO {

    @NotNull(message = "O ongId e obrigatorio")
    @Positive(message = "O ongId deve ser um numero positivo")
    private Long ongId;

    @NotNull(message = "O doadorId e obrigatorio")
    @Positive(message = "O doadorId deve ser um numero positivo")
    private Long doadorId;

    @NotNull(message = "O valor e obrigatorio")
    @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
    @DecimalMax(value = "1000000.00", message = "Valor acima do limite permitido")
    private Double valor;

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }
}
