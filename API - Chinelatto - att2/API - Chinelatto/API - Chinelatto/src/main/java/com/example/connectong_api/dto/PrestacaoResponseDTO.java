package com.example.connectong_api.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PrestacaoResponseDTO {

    private Long id;
    private Long interesseId;
    private String titulo;
    private String descricao;
    private String fotoUrl;
    private LocalDateTime dataCriacao;

    // Contexto do match (facilita as telas sem novas chamadas).
    private Long doadorId;
    private String doadorNome;
    private String ongNome;
    private String necessidadeTitulo;

    // Fotos embutidas (base64) e valor utilizado (transparencia).
    private List<String> fotos;
    private Double valorUtilizado;

    public PrestacaoResponseDTO(
            Long id,
            Long interesseId,
            String titulo,
            String descricao,
            String fotoUrl,
            LocalDateTime dataCriacao,
            Long doadorId,
            String doadorNome,
            String ongNome,
            String necessidadeTitulo,
            List<String> fotos,
            Double valorUtilizado
    ) {
        this.id = id;
        this.interesseId = interesseId;
        this.titulo = titulo;
        this.descricao = descricao;
        this.fotoUrl = fotoUrl;
        this.dataCriacao = dataCriacao;
        this.doadorId = doadorId;
        this.doadorNome = doadorNome;
        this.ongNome = ongNome;
        this.necessidadeTitulo = necessidadeTitulo;
        this.fotos = fotos;
        this.valorUtilizado = valorUtilizado;
    }

    public Long getId() { return id; }
    public Long getInteresseId() { return interesseId; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public String getFotoUrl() { return fotoUrl; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public Long getDoadorId() { return doadorId; }
    public String getDoadorNome() { return doadorNome; }
    public String getOngNome() { return ongNome; }
    public String getNecessidadeTitulo() { return necessidadeTitulo; }
    public List<String> getFotos() { return fotos; }
    public Double getValorUtilizado() { return valorUtilizado; }
}
