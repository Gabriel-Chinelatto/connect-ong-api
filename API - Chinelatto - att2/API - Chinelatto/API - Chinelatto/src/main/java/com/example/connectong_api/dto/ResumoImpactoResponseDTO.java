package com.example.connectong_api.dto;

/**
 * Resposta de POST /ia/resumo-impacto.
 *
 * { "resumo": "parágrafo curto e caloroso sobre a atuação da ONG", "modo": "ia"|"regras" }
 */
public class ResumoImpactoResponseDTO {

    private String resumo;
    private String modo;

    public ResumoImpactoResponseDTO() {}

    public ResumoImpactoResponseDTO(String resumo, String modo) {
        this.resumo = resumo;
        this.modo = modo;
    }

    public String getResumo() { return resumo; }
    public void setResumo(String resumo) { this.resumo = resumo; }

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
}
