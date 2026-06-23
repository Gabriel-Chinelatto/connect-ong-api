package com.example.connectong_api.dto;

import java.time.LocalDateTime;

/**
 * Dados de saida de uma mensagem do chat.
 */
public class MensagemResponseDTO {

    private Long id;
    private Long interesseId;
    private String remetente;
    private String conteudo;
    private LocalDateTime dataEnvio;

    public MensagemResponseDTO(
            Long id,
            Long interesseId,
            String remetente,
            String conteudo,
            LocalDateTime dataEnvio
    ) {
        this.id = id;
        this.interesseId = interesseId;
        this.remetente = remetente;
        this.conteudo = conteudo;
        this.dataEnvio = dataEnvio;
    }

    public Long getId() { return id; }
    public Long getInteresseId() { return interesseId; }
    public String getRemetente() { return remetente; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getDataEnvio() { return dataEnvio; }
}
