package com.example.connectong_api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

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

    // PIX em 2 fases (opcional): codigo "copia e cola" ja gerado na fase 1
    // (POST /doacoes-financeiras/gerar-codigo). Quando presente, e registrado
    // como comprovante em vez de gerar um novo.
    @Size(max = 512, message = "Codigo PIX muito longo")
    private String codigoPix;

    // Campanha a qual esta doacao contribui (opcional). Quando presente e
    // valida, incrementa o valor arrecadado (e pode auto-encerrar a campanha).
    @Positive(message = "O campanhaId deve ser um numero positivo")
    private Long campanhaId;

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }

    public String getCodigoPix() { return codigoPix; }
    public void setCodigoPix(String codigoPix) { this.codigoPix = codigoPix; }

    public Long getCampanhaId() { return campanhaId; }
    public void setCampanhaId(Long campanhaId) { this.campanhaId = campanhaId; }
}
