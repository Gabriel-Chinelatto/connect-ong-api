package com.example.connectong_api.dto;

/**
 * Dados de entrada para a ONG publicar uma prestacao de contas num match.
 */
public class PrestacaoRequestDTO {

    private Long interesseId;
    private String titulo;
    private String descricao;
    private String fotoUrl;

    public Long getInteresseId() { return interesseId; }
    public void setInteresseId(Long interesseId) { this.interesseId = interesseId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }
}
