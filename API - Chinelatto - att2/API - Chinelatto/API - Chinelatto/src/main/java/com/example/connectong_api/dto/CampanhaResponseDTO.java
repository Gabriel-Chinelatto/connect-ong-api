package com.example.connectong_api.dto;

import com.example.connectong_api.model.Campanha;

import java.time.LocalDate;

public class CampanhaResponseDTO {

    private Long id;
    private String titulo;
    private String descricao;
    private Double metaValor;
    private Double valorArrecadado;
    private int progresso;        // % de 0 a 100
    private String categoria;
    private LocalDate dataInicio;
    private LocalDate dataFim;
    private boolean encerrada;
    private boolean destaque;
    private Long ongId;
    private String ongNome;

    public CampanhaResponseDTO(Campanha c) {
        this.id = c.getId();
        this.titulo = c.getTitulo();
        this.descricao = c.getDescricao();
        this.metaValor = c.getMetaValor();
        this.valorArrecadado = c.getValorArrecadado();
        this.categoria = c.getCategoria();
        this.dataInicio = c.getDataInicio();
        this.dataFim = c.getDataFim();
        this.encerrada = c.getEncerrada();
        this.destaque = c.getDestaque();
        this.ongId = c.getOng() != null ? c.getOng().getId() : null;
        this.ongNome = c.getOng() != null ? c.getOng().getNome() : null;

        double meta = c.getMetaValor() != null ? c.getMetaValor() : 0;
        if (meta > 0) {
            int p = (int) Math.round(c.getValorArrecadado() / meta * 100);
            this.progresso = Math.max(0, Math.min(100, p));
        } else {
            this.progresso = 0;
        }
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public Double getMetaValor() { return metaValor; }
    public Double getValorArrecadado() { return valorArrecadado; }
    public int getProgresso() { return progresso; }
    public String getCategoria() { return categoria; }
    public LocalDate getDataInicio() { return dataInicio; }
    public LocalDate getDataFim() { return dataFim; }
    public boolean isEncerrada() { return encerrada; }
    public boolean isDestaque() { return destaque; }
    public Long getOngId() { return ongId; }
    public String getOngNome() { return ongNome; }
}
