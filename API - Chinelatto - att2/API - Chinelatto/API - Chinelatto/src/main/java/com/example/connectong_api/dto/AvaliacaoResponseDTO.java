package com.example.connectong_api.dto;

import java.time.LocalDateTime;

public class AvaliacaoResponseDTO {

    private Long id;
    private Long ongId;
    private String doadorNome;
    private Integer nota;
    private String comentario;
    private LocalDateTime dataCriacao;

    public AvaliacaoResponseDTO(
            Long id,
            Long ongId,
            String doadorNome,
            Integer nota,
            String comentario,
            LocalDateTime dataCriacao
    ) {
        this.id = id;
        this.ongId = ongId;
        this.doadorNome = doadorNome;
        this.nota = nota;
        this.comentario = comentario;
        this.dataCriacao = dataCriacao;
    }

    public Long getId() { return id; }
    public Long getOngId() { return ongId; }
    public String getDoadorNome() { return doadorNome; }
    public Integer getNota() { return nota; }
    public String getComentario() { return comentario; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
