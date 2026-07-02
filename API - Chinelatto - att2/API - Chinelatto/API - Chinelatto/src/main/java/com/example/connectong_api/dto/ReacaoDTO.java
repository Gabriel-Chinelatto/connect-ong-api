package com.example.connectong_api.dto;

/**
 * Reacao de saida: o codigo do emoji (LIKE, LOVE, ...) e o lado que reagiu
 * (DOADOR/ONG). O app traduz o codigo para o emoji e destaca a reacao propria.
 */
public class ReacaoDTO {

    private String emoji;
    private String lado;

    public ReacaoDTO(String emoji, String lado) {
        this.emoji = emoji;
        this.lado = lado;
    }

    public String getEmoji() { return emoji; }
    public String getLado() { return lado; }
}
