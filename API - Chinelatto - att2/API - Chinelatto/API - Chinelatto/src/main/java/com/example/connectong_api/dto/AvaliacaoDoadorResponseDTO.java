package com.example.connectong_api.dto;

import java.time.LocalDateTime;

/**
 * Uma avaliacao recebida por um doador (feita por uma ONG). Endpoint publico:
 * expoe so o nome da ONG, a nota, o comentario e a data — nada do doador.
 */
public class AvaliacaoDoadorResponseDTO {

    private String ongNome;
    private Integer nota;
    private String comentario;
    private LocalDateTime criadoEm;

    public AvaliacaoDoadorResponseDTO(String ongNome, Integer nota,
                                      String comentario, LocalDateTime criadoEm) {
        this.ongNome = ongNome;
        this.nota = nota;
        this.comentario = comentario;
        this.criadoEm = criadoEm;
    }

    public String getOngNome() { return ongNome; }
    public Integer getNota() { return nota; }
    public String getComentario() { return comentario; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
