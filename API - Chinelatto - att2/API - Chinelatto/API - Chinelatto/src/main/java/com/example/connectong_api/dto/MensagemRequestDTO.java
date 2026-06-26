package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para enviar uma mensagem no chat de um match.
 */
public class MensagemRequestDTO {

    @NotNull(message = "O interesseId e obrigatorio")
    @Positive(message = "O interesseId deve ser um numero positivo")
    private Long interesseId;

    @NotBlank(message = "O remetente e obrigatorio")
    @Pattern(regexp = "DOADOR|ONG", message = "O remetente deve ser DOADOR ou ONG")
    private String remetente; // "DOADOR" ou "ONG"

    @NotBlank(message = "A mensagem nao pode ser vazia")
    @Size(max = 2000, message = "A mensagem deve ter no maximo 2000 caracteres")
    private String conteudo;

    public Long getInteresseId() { return interesseId; }
    public void setInteresseId(Long interesseId) { this.interesseId = interesseId; }

    public String getRemetente() { return remetente; }
    public void setRemetente(String remetente) { this.remetente = remetente; }

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }
}
