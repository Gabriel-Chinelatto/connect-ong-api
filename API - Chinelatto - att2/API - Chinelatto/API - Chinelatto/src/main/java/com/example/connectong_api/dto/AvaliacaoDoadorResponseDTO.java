package com.example.connectong_api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Uma avaliacao recebida por um doador (feita por uma ONG). Endpoint publico:
 * expoe so o nome da ONG, a nota, o comentario, as fotos da doacao e a data —
 * nada que identifique/exponha o doador.
 */
public class AvaliacaoDoadorResponseDTO {

    private String ongNome;
    private Integer nota;
    private String comentario;
    private List<String> fotos;
    private LocalDateTime criadoEm;

    public AvaliacaoDoadorResponseDTO(String ongNome, Integer nota,
                                      String comentario, List<String> fotos,
                                      LocalDateTime criadoEm) {
        this.ongNome = ongNome;
        this.nota = nota;
        this.comentario = comentario;
        this.fotos = fotos;
        this.criadoEm = criadoEm;
    }

    public String getOngNome() { return ongNome; }
    public Integer getNota() { return nota; }
    public String getComentario() { return comentario; }
    public List<String> getFotos() { return fotos; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
