package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de edicao de uma necessidade existente (PUT /necessidades/{id}). Nao
 * inclui ongId: o dono e resolvido pela propria necessidade e conferido contra
 * o token (so a ONG dona edita). categoria e normalizada como no POST.
 */
public class NecessidadeUpdateDTO {

    @NotBlank(message = "O titulo e obrigatorio")
    @Size(min = 3, max = 150, message = "O titulo deve ter entre 3 e 150 caracteres")
    private String titulo;

    @NotBlank(message = "A descricao e obrigatoria")
    @Size(max = 2000, message = "A descricao deve ter no maximo 2000 caracteres")
    private String descricao;

    @Size(max = 60, message = "Categoria muito longa")
    private String categoria;

    private Boolean urgente;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public Boolean getUrgente() { return urgente; }
    public void setUrgente(Boolean urgente) { this.urgente = urgente; }
}
