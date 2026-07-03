package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /campanhas/{id}/contribuir, tipado (correcao do bug B2):
 * antes o controller fazia Double.valueOf sobre um Map cru e um valor nao
 * numerico virava NumberFormatException -> 500. Com o DTO, entrada invalida
 * vira 400 com mensagem de campo. Os NOMES dos campos sao os mesmos que os
 * apps ja enviam (valor, doadorNome) — o contrato nao muda, apenas a tipagem.
 */
public class ContribuicaoRequestDTO {

    @NotNull(message = "O valor deve ser maior que zero")
    @Positive(message = "O valor deve ser maior que zero")
    private Double valor;

    @Size(max = 120, message = "Nome do doador muito longo")
    private String doadorNome;

    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }

    public String getDoadorNome() { return doadorNome; }
    public void setDoadorNome(String doadorNome) { this.doadorNome = doadorNome; }
}
