package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /ia/sobre-ong (ajuda a ONG a escrever/refinar o texto "Sobre"
 * institucional do perfil dela, com loop de ajuste em linguagem natural).
 *
 * {
 *   "ongId": 12,                                  (obrigatorio)
 *   "rascunho": "texto atual/base da ONG",        (opcional; max 600 chars)
 *   "ajuste": "deixe mais curto"                  (opcional; max 300 chars)
 * }
 *
 * - rascunho: o texto BASE. Na 1a chamada, o que a ONG digitou; nas seguintes, o
 *   texto que a IA ja sugeriu (a ONG reenvia para continuar refinando).
 * - ajuste: o que a ONG pediu para mudar (vazio na 1a chamada). Ex.: "mais curto",
 *   "mencione que atendemos criancas".
 */
public class SobreOngRequestDTO {

    @NotNull(message = "O id da ONG é obrigatório")
    private Long ongId;

    @Size(max = 600, message = "Rascunho muito longo (máx. 600 caracteres)")
    private String rascunho;

    @Size(max = 300, message = "Ajuste muito longo (máx. 300 caracteres)")
    private String ajuste;

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public String getRascunho() { return rascunho; }
    public void setRascunho(String rascunho) { this.rascunho = rascunho; }

    public String getAjuste() { return ajuste; }
    public void setAjuste(String ajuste) { this.ajuste = ajuste; }
}
