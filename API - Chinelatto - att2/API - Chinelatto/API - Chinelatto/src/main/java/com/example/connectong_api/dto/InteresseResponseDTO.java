package com.example.connectong_api.dto;

import java.time.LocalDateTime;

/**
 * Dados de saida de um interesse/match.
 * Traz info dos dois lados (doador e ONG) para facilitar as telas.
 */
public class InteresseResponseDTO {

    private Long id;
    private String status;
    private LocalDateTime dataCriacao;

    // Quando a ONG marcou o match como CONCLUIDO (null nos demais status).
    private LocalDateTime dataConclusao;

    private Long necessidadeId;
    private String necessidadeTitulo;

    private Long doadorId;
    private String doadorNome;

    private Long ongId;
    private String ongNome;

    public InteresseResponseDTO(
            Long id,
            String status,
            LocalDateTime dataCriacao,
            LocalDateTime dataConclusao,
            Long necessidadeId,
            String necessidadeTitulo,
            Long doadorId,
            String doadorNome,
            Long ongId,
            String ongNome
    ) {
        this.id = id;
        this.status = status;
        this.dataCriacao = dataCriacao;
        this.dataConclusao = dataConclusao;
        this.necessidadeId = necessidadeId;
        this.necessidadeTitulo = necessidadeTitulo;
        this.doadorId = doadorId;
        this.doadorNome = doadorNome;
        this.ongId = ongId;
        this.ongNome = ongNome;
    }

    public Long getId() { return id; }
    public String getStatus() { return status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public LocalDateTime getDataConclusao() { return dataConclusao; }
    public Long getNecessidadeId() { return necessidadeId; }
    public String getNecessidadeTitulo() { return necessidadeTitulo; }
    public Long getDoadorId() { return doadorId; }
    public String getDoadorNome() { return doadorNome; }
    public Long getOngId() { return ongId; }
    public String getOngNome() { return ongNome; }
}
