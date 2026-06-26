package com.example.connectong_api.dto;

/**
 * Numeros publicos da plataforma, exibidos no portal institucional
 * (transparencia / mural de impacto). Sem dados pessoais.
 */
public class EstatisticasPublicasDTO {

    private long totalOngs;
    private long totalDoadores;
    private long totalNecessidades;
    private long totalMatches;            // interesses aceitos (doacoes conectadas)
    private long totalDoacoesFinanceiras;
    private double valorTotalDoado;
    private long totalPrestacoes;         // prestacoes de contas publicadas

    public long getTotalOngs() { return totalOngs; }
    public void setTotalOngs(long totalOngs) { this.totalOngs = totalOngs; }

    public long getTotalDoadores() { return totalDoadores; }
    public void setTotalDoadores(long totalDoadores) { this.totalDoadores = totalDoadores; }

    public long getTotalNecessidades() { return totalNecessidades; }
    public void setTotalNecessidades(long totalNecessidades) { this.totalNecessidades = totalNecessidades; }

    public long getTotalMatches() { return totalMatches; }
    public void setTotalMatches(long totalMatches) { this.totalMatches = totalMatches; }

    public long getTotalDoacoesFinanceiras() { return totalDoacoesFinanceiras; }
    public void setTotalDoacoesFinanceiras(long totalDoacoesFinanceiras) { this.totalDoacoesFinanceiras = totalDoacoesFinanceiras; }

    public double getValorTotalDoado() { return valorTotalDoado; }
    public void setValorTotalDoado(double valorTotalDoado) { this.valorTotalDoado = valorTotalDoado; }

    public long getTotalPrestacoes() { return totalPrestacoes; }
    public void setTotalPrestacoes(long totalPrestacoes) { this.totalPrestacoes = totalPrestacoes; }
}
