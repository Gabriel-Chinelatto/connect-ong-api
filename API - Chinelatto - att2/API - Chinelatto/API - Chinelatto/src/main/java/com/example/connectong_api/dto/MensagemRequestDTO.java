package com.example.connectong_api.dto;

/**
 * Dados de entrada para enviar uma mensagem no chat de um match.
 */
public class MensagemRequestDTO {

    private Long interesseId;
    private String remetente; // "DOADOR" ou "ONG"
    private String conteudo;

    public Long getInteresseId() { return interesseId; }
    public void setInteresseId(Long interesseId) { this.interesseId = interesseId; }

    public String getRemetente() { return remetente; }
    public void setRemetente(String remetente) { this.remetente = remetente; }

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }
}
