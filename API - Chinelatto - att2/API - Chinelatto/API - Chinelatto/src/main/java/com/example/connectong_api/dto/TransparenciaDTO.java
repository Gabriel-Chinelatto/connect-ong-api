package com.example.connectong_api.dto;

import com.example.connectong_api.model.Ong;

/**
 * Indice de transparencia de uma ONG: pontua sinais publicos de confianca
 * (selo de verificacao, avaliacoes, prestacoes de contas, campanhas
 * concluidas) num score 0-100 e um nivel BRONZE/PRATA/OURO. Serve tanto para
 * o endpoint individual quanto como item do ranking.
 */
public class TransparenciaDTO {

    private Long ongId;
    private String nome;
    private String cidade;
    private boolean verificada;
    private double notaMedia;
    private int totalAvaliacoes;
    private long totalPrestacoes;
    private long campanhasConcluidas;
    private int score;       // 0-100
    private String nivel;    // BRONZE | PRATA | OURO

    public TransparenciaDTO(Ong o, int score, String nivel,
                            long totalPrestacoes, long campanhasConcluidas) {
        this.ongId = o.getId();
        this.nome = o.getNome();
        this.cidade = o.getCidade();
        this.verificada = o.getVerificada();
        this.notaMedia = o.getNotaMedia();
        this.totalAvaliacoes = o.getTotalAvaliacoes();
        this.totalPrestacoes = totalPrestacoes;
        this.campanhasConcluidas = campanhasConcluidas;
        this.score = score;
        this.nivel = nivel;
    }

    public Long getOngId() { return ongId; }
    public String getNome() { return nome; }
    public String getCidade() { return cidade; }
    public boolean isVerificada() { return verificada; }
    public double getNotaMedia() { return notaMedia; }
    public int getTotalAvaliacoes() { return totalAvaliacoes; }
    public long getTotalPrestacoes() { return totalPrestacoes; }
    public long getCampanhasConcluidas() { return campanhasConcluidas; }
    public int getScore() { return score; }
    public String getNivel() { return nivel; }
}
