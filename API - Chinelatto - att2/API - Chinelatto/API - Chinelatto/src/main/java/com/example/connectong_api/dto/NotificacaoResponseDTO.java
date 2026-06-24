package com.example.connectong_api.dto;

import java.time.LocalDateTime;

public class NotificacaoResponseDTO {

    private Long id;
    private String titulo;
    private String mensagem;
    private String tipo;
    private Boolean lida;
    private LocalDateTime dataCriacao;

    public NotificacaoResponseDTO(
            Long id,
            String titulo,
            String mensagem,
            String tipo,
            Boolean lida,
            LocalDateTime dataCriacao
    ) {
        this.id = id;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.tipo = tipo;
        this.lida = lida;
        this.dataCriacao = dataCriacao;
    }

    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getMensagem() { return mensagem; }
    public String getTipo() { return tipo; }
    public Boolean getLida() { return lida; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
