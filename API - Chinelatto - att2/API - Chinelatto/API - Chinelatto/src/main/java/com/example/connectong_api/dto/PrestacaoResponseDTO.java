package com.example.connectong_api.dto;

import java.time.LocalDateTime;

public class PrestacaoResponseDTO {

    private Long id;
    private Long interesseId;
    private String titulo;
    private String descricao;
    private String fotoUrl;
    private LocalDateTime dataCriacao;

    public PrestacaoResponseDTO(
            Long id,
            Long interesseId,
            String titulo,
            String descricao,
            String fotoUrl,
            LocalDateTime dataCriacao
    ) {
        this.id = id;
        this.interesseId = interesseId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.fotoUrl = fotoUrl;
        this.dataCriacao = dataCriacao;
    }

    public Long getId() { return id; }
    public Long getInteresseId() { return interesseId; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public String getFotoUrl() { return fotoUrl; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
