package com.example.connectong_api.dto;

/**
 * Conquista (gamificacao) derivada de dados ja existentes. Sem tabela propria:
 * o estado (conquistada ou nao) e calculado a partir de contagens.
 */
public class ConquistaDTO {

    private String chave;        // ex.: PRIMEIRA_DOACAO (o frontend mapeia o icone)
    private String titulo;
    private String descricao;
    private boolean conquistada;

    public ConquistaDTO(String chave, String titulo, String descricao, boolean conquistada) {
        this.chave = chave;
        this.titulo = titulo;
        this.descricao = descricao;
        this.conquistada = conquistada;
    }

    public String getChave() { return chave; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public boolean isConquistada() { return conquistada; }
}
