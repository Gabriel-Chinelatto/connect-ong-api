package com.example.connectong_api.dto;

public class AvaliacaoRequestDTO {

    private Long ongId;
    private Long doadorId;
    private Integer nota;
    private String comentario;

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public Integer getNota() { return nota; }
    public void setNota(Integer nota) { this.nota = nota; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
}
