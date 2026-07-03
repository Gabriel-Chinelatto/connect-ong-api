package com.example.connectong_api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para uma ONG avaliar um DOADOR (nota 1-5 + comentario).
 * A ONG avaliadora vem do TOKEN (nunca do corpo).
 */
public class AvaliacaoDoadorRequestDTO {

    @NotNull(message = "O doadorId e obrigatorio")
    @Positive(message = "O doadorId deve ser um numero positivo")
    private Long doadorId;

    @NotNull(message = "A nota e obrigatoria")
    @Min(value = 1, message = "A nota deve ser de 1 a 5")
    @Max(value = 5, message = "A nota deve ser de 1 a 5")
    private Integer nota;

    @Size(max = 500, message = "O comentario deve ter no maximo 500 caracteres")
    private String comentario;

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public Integer getNota() { return nota; }
    public void setNota(Integer nota) { this.nota = nota; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }
}
