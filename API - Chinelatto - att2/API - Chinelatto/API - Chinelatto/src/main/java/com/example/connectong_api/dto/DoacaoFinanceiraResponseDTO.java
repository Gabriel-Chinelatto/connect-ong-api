package com.example.connectong_api.dto;

import java.time.LocalDateTime;

public class DoacaoFinanceiraResponseDTO {

    private Long id;
    private Long ongId;
    private String ongNome;
    private String doadorNome;
    private Double valor;
    private String codigoPix;
    private String status;
    private LocalDateTime dataCriacao;

    public DoacaoFinanceiraResponseDTO(
            Long id,
            Long ongId,
            String ongNome,
            String doadorNome,
            Double valor,
            String codigoPix,
            String status,
            LocalDateTime dataCriacao
    ) {
        this.id = id;
        this.ongId = ongId;
        this.ongNome = ongNome;
        this.doadorNome = doadorNome;
        this.valor = valor;
        this.codigoPix = codigoPix;
        this.status = status;
        this.dataCriacao = dataCriacao;
    }

    public Long getId() { return id; }
    public Long getOngId() { return ongId; }
    public String getOngNome() { return ongNome; }
    public String getDoadorNome() { return doadorNome; }
    public Double getValor() { return valor; }
    public String getCodigoPix() { return codigoPix; }
    public String getStatus() { return status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
