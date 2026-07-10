package com.example.connectong_api.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Resposta de POST /frete/estimar.
 *
 * {
 *   "origem": "Limeira/SP",
 *   "destino": "Rio de Janeiro/RJ",
 *   "distanciaKm": 380,
 *   "pesoKg": 15.0,
 *   "pesoEstimado": true,          (true quando o peso veio do ItemIaService)
 *   "categoria": "Roupas",
 *   "itemResumo": "10x cobertores",
 *   "modalidades": [ {"nome":"...","valor":0.0,"prazoDias":0,"detalhe":"..."} ],
 *   "aviso": "Valores estimados por distância e peso — não são cotação oficial.",
 *   "modo": "ia" | "regras"
 * }
 *
 * Valores SEMPRE estimados (nunca cotacao oficial). "Entrega combinada" (valor 0,
 * prazo 0) so aparece quando a distancia e curta (<= raio local, mesma regiao).
 */
public class FreteResponseDTO {

    private String origem;
    private String destino;
    private Integer distanciaKm;
    private Double pesoKg;
    private boolean pesoEstimado;
    private String categoria;
    private String itemResumo;
    private List<Modalidade> modalidades = new ArrayList<>();
    private String aviso;
    private String modo;

    public FreteResponseDTO() {}

    public String getOrigem() { return origem; }
    public void setOrigem(String origem) { this.origem = origem; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public Integer getDistanciaKm() { return distanciaKm; }
    public void setDistanciaKm(Integer distanciaKm) { this.distanciaKm = distanciaKm; }

    public Double getPesoKg() { return pesoKg; }
    public void setPesoKg(Double pesoKg) { this.pesoKg = pesoKg; }

    public boolean isPesoEstimado() { return pesoEstimado; }
    public void setPesoEstimado(boolean pesoEstimado) { this.pesoEstimado = pesoEstimado; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getItemResumo() { return itemResumo; }
    public void setItemResumo(String itemResumo) { this.itemResumo = itemResumo; }

    public List<Modalidade> getModalidades() { return modalidades; }
    public void setModalidades(List<Modalidade> modalidades) { this.modalidades = modalidades; }

    public String getAviso() { return aviso; }
    public void setAviso(String aviso) { this.aviso = aviso; }

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }

    /** Uma opcao de frete: nome, valor (R$), prazo em dias e um detalhe curto. */
    public static class Modalidade {
        private String nome;
        private double valor;
        private int prazoDias;
        private String detalhe;

        public Modalidade() {}

        public Modalidade(String nome, double valor, int prazoDias, String detalhe) {
            this.nome = nome;
            this.valor = valor;
            this.prazoDias = prazoDias;
            this.detalhe = detalhe;
        }

        public String getNome() { return nome; }
        public void setNome(String nome) { this.nome = nome; }

        public double getValor() { return valor; }
        public void setValor(double valor) { this.valor = valor; }

        public int getPrazoDias() { return prazoDias; }
        public void setPrazoDias(int prazoDias) { this.prazoDias = prazoDias; }

        public String getDetalhe() { return detalhe; }
        public void setDetalhe(String detalhe) { this.detalhe = detalhe; }
    }
}
