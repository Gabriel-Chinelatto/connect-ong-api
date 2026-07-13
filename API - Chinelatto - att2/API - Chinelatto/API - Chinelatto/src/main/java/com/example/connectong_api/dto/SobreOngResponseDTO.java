package com.example.connectong_api.dto;

/**
 * Resposta de POST /ia/sobre-ong.
 *
 * { "descricao": "texto 'Sobre' institucional (2 a 4 frases)", "modo": "ia"|"regras" }
 */
public class SobreOngResponseDTO {

    private String descricao;
    private String modo;

    public SobreOngResponseDTO() {}

    public SobreOngResponseDTO(String descricao, String modo) {
        this.descricao = descricao;
        this.modo = modo;
    }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
}
