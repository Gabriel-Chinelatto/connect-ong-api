package com.example.connectong_api.model;

import jakarta.persistence.*;

/**
 * Item de doacao do catalogo (o que se oferece/precisa), com categoria, quantidade
 * e marcadores de {@code urgente} e {@code novo}.
 *
 * {@code doadorId} = dono do item (o doador que cadastrou). Fica null nas linhas
 * antigas (pre-Fase 5); e definido a partir do token no cadastro. Permite a
 * listagem "Minhas Doacoes" por usuario (GET /doacoes/minhas).
 */
@Entity
@Table(name = "doacao")
public class Doacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String descricao;
    private Integer quantidade;
    private String categoria;
    private String tipo;
    private Boolean urgente;
    private Boolean novo;

    // Dono do item (doador que cadastrou). Null nas linhas legadas.
    private Long doadorId;

    public Doacao(){}

    public Long getId() { return id; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Integer getQuantidade() { return quantidade; }
    public void setQuantidade(Integer quantidade) { this.quantidade = quantidade; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Boolean getUrgente() { return urgente; }
    public void setUrgente(Boolean urgente) { this.urgente = urgente; }

    public Boolean getNovo() { return novo; }
    public void setNovo(Boolean novo) { this.novo = novo; }
}