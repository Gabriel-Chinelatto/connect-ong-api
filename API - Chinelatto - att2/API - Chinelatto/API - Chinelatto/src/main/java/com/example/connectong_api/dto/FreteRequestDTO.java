package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /frete/estimar.
 *
 * {
 *   "origemCidade": "Limeira",      (obrigatorio)
 *   "origemUf": "SP",               (opcional; ajuda a desambiguar cidades homonimas)
 *   "destinoCidade": "Rio de Janeiro", (obrigatorio)
 *   "destinoUf": "RJ",              (opcional)
 *   "item": "10 cobertores",        (opcional; usado p/ estimar peso/categoria se pesoKg ausente)
 *   "categoria": "Roupas",          (opcional)
 *   "quantidade": 10,               (opcional; multiplica o peso unitario)
 *   "pesoKg": 15.0                  (opcional; se informado (>0) dispensa a estimativa)
 * }
 *
 * origemCidade e destinoCidade sao obrigatorias; o resto e opcional. Quando
 * pesoKg nao vem, o service estima o peso a partir de item/quantidade (ItemIaService).
 */
public class FreteRequestDTO {

    @NotBlank(message = "A cidade de origem é obrigatória")
    @Size(max = 80, message = "Cidade de origem muito longa")
    private String origemCidade;

    @Size(max = 2, message = "UF de origem inválida")
    private String origemUf;

    @NotBlank(message = "A cidade de destino é obrigatória")
    @Size(max = 80, message = "Cidade de destino muito longa")
    private String destinoCidade;

    @Size(max = 2, message = "UF de destino inválida")
    private String destinoUf;

    @Size(max = 200, message = "Descrição do item muito longa")
    private String item;

    @Size(max = 200, message = "Categoria muito longa")
    private String categoria;

    private Integer quantidade;

    private Double pesoKg;

    public String getOrigemCidade() { return origemCidade; }
    public void setOrigemCidade(String origemCidade) { this.origemCidade = origemCidade; }

    public String getOrigemUf() { return origemUf; }
    public void setOrigemUf(String origemUf) { this.origemUf = origemUf; }

    public String getDestinoCidade() { return destinoCidade; }
    public void setDestinoCidade(String destinoCidade) { this.destinoCidade = destinoCidade; }

    public String getDestinoUf() { return destinoUf; }
    public void setDestinoUf(String destinoUf) { this.destinoUf = destinoUf; }

    public String getItem() { return item; }
    public void setItem(String item) { this.item = item; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public Double getPesoKg() { return pesoKg; }
    public void setPesoKg(Double pesoKg) { this.pesoKg = pesoKg; }
}
