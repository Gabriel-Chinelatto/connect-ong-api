package com.example.connectong_api.dto;

import com.example.connectong_api.model.Favorito;

import java.time.LocalDateTime;

public class FavoritoResponseDTO {

    private Long id;
    private String tipo;
    private Long alvoId;
    private String alvoNome;   // nome da ONG ou titulo da campanha (resolvido)
    private LocalDateTime dataCriacao;

    public FavoritoResponseDTO(Favorito f, String alvoNome) {
        this.id = f.getId();
        this.tipo = f.getTipo();
        this.alvoId = f.getAlvoId();
        this.alvoNome = alvoNome;
        this.dataCriacao = f.getDataCriacao();
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public Long getAlvoId() { return alvoId; }
    public String getAlvoNome() { return alvoNome; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
