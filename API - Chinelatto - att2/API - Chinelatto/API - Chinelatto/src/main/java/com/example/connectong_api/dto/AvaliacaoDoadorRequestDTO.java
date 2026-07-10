package com.example.connectong_api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Dados de entrada para uma ONG avaliar um DOADOR (nota 1-5 + comentario +
 * fotos opcionais da doacao recebida). A ONG avaliadora vem do TOKEN.
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

    // Fotos opcionais (base64) da doacao recebida. No maximo 3; o tamanho de
    // cada uma e validado no service (mesmo teto das prestacoes).
    @Size(max = 3, message = "No maximo 3 fotos por avaliacao")
    private List<String> fotos;

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public Integer getNota() { return nota; }
    public void setNota(Integer nota) { this.nota = nota; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public List<String> getFotos() { return fotos; }
    public void setFotos(List<String> fotos) { this.fotos = fotos; }
}
