package com.example.connectong_api.dto;

/**
 * Resposta de POST /ia/redacao.
 *
 * {
 *   "titulo": "Cobertores para o inverno",
 *   "descricao": "2 a 4 frases calorosas, especificas, sem inventar dados.",
 *   "modo": "ia" | "regras"
 * }
 */
public class RedacaoResponseDTO {

    private String titulo;
    private String descricao;
    private String modo;

    public RedacaoResponseDTO() {}

    public RedacaoResponseDTO(String titulo, String descricao, String modo) {
        this.titulo = titulo;
        this.descricao = descricao;
        this.modo = modo;
    }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }
}
