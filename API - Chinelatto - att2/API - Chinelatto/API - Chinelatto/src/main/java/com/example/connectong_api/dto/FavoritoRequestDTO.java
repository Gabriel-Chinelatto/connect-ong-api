package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corpo de POST /favoritos, tipado (correcao do bug B2): antes o controller
 * fazia Long.valueOf sobre um Map cru e qualquer valor nao numerico virava
 * NumberFormatException -> 500. Com o DTO, entrada invalida vira 400 com
 * mensagem de campo. Os NOMES dos campos sao os mesmos que os apps ja enviam
 * (usuarioId, tipo, alvoId) — o contrato nao muda, apenas a tipagem.
 */
public class FavoritoRequestDTO {

    @NotNull(message = "Informe o usuarioId")
    @Positive(message = "usuarioId inválido")
    private Long usuarioId;

    @NotBlank(message = "Informe o tipo (ONG ou CAMPANHA)")
    private String tipo;

    @NotNull(message = "Informe o alvoId")
    @Positive(message = "alvoId inválido")
    private Long alvoId;

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Long getAlvoId() { return alvoId; }
    public void setAlvoId(Long alvoId) { this.alvoId = alvoId; }
}
