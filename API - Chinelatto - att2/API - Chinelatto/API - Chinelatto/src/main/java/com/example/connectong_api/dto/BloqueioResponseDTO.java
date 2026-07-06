package com.example.connectong_api.dto;

import java.time.LocalDateTime;

/**
 * Item da lista GET /bloqueios (os doadores que a ONG autenticada bloqueou).
 */
public class BloqueioResponseDTO {

    private Long doadorId;
    private String doadorNome;
    private LocalDateTime criadoEm;

    public BloqueioResponseDTO(Long doadorId, String doadorNome, LocalDateTime criadoEm) {
        this.doadorId = doadorId;
        this.doadorNome = doadorNome;
        this.criadoEm = criadoEm;
    }

    public Long getDoadorId() { return doadorId; }
    public String getDoadorNome() { return doadorNome; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
