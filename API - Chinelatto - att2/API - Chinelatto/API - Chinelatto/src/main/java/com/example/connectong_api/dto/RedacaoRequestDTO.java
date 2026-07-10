package com.example.connectong_api.dto;

import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /ia/redacao (ajuda a ONG a escrever uma necessidade no painel).
 *
 * {
 *   "titulo": "Cobertores",              (opcional)
 *   "rascunho": "preciso de cobertor pro frio", (opcional; max 600 chars)
 *   "categoria": "Roupas"                (opcional)
 * }
 *
 * Pelo menos rascunho OU titulo deve vir preenchido (validado no service).
 */
public class RedacaoRequestDTO {

    @Size(max = 120, message = "Título muito longo")
    private String titulo;

    @Size(max = 600, message = "Rascunho muito longo (máx. 600 caracteres)")
    private String rascunho;

    @Size(max = 60, message = "Categoria muito longa")
    private String categoria;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getRascunho() { return rascunho; }
    public void setRascunho(String rascunho) { this.rascunho = rascunho; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
}
