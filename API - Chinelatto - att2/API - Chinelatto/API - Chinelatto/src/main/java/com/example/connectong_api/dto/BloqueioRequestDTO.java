package com.example.connectong_api.dto;

/**
 * Corpo do POST /bloqueios: {"doadorId": 123}. O ongId NUNCA vem do cliente —
 * e sempre o da ONG autenticada (token).
 */
public class BloqueioRequestDTO {

    private Long doadorId;

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }
}
