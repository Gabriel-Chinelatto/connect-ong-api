package com.example.connectong_api.dto;

import com.example.connectong_api.model.Atividade;

import java.time.LocalDateTime;

public class AtividadeResponseDTO {

    private Long id;
    private String tipo;
    private String descricao;
    private Long ongId;
    private String ongNome;
    private LocalDateTime dataCriacao;

    public AtividadeResponseDTO(Atividade a) {
        this.id = a.getId();
        this.tipo = a.getTipo();
        this.descricao = a.getDescricao();
        this.ongId = a.getOngId();
        this.ongNome = a.getOngNome();
        this.dataCriacao = a.getDataCriacao();
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public Long getOngId() { return ongId; }
    public String getOngNome() { return ongNome; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
