package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Corpo de POST /ia/resumo-impacto: { "ongId": 123 }.
 * O backend coleta os numeros REAIS da ONG e resume o impacto para o doador.
 */
public class ResumoImpactoRequestDTO {

    @NotNull(message = "ongId é obrigatório")
    private Long ongId;

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }
}
