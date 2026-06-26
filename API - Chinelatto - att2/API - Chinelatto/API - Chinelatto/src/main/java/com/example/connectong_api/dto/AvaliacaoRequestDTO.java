package com.example.connectong_api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class AvaliacaoRequestDTO {

    @NotNull(message = "O ongId e obrigatorio")
    @Positive(message = "O ongId deve ser um numero positivo")
    private Long ongId;

    @NotNull(message = "O doadorId e obrigatorio")
    @Positive(message = "O doadorId deve ser um numero positivo")
    private Long doadorId;

    @NotNull(message = "A nota e obrigatoria")
    @Min(value = 1, message = "A nota minima e 1")
    @Max(value = 5, message = "A nota maxima e 5")
    private Integer nota;

    @Size(max = 1000, message = "O comentario deve ter no maximo 1000 caracteres")
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
