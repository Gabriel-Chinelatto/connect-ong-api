package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Reacao (emoji) de um participante a uma mensagem do chat — estilo WhatsApp.
 *
 * IMPORTANTE: guardamos um CODIGO da reacao (ex.: "LIKE", "LOVE"), nunca o emoji
 * em si. O MySQL 5.6 da escola usa utf8 de 3 bytes e um emoji (4 bytes) causaria
 * "Incorrect string value" (erro 500). O app traduz o codigo para o emoji.
 *
 * Regra: no maximo 1 reacao por usuario por mensagem (a de cada lado do match).
 * Vinculos por id simples (usuarioId, mensagemId), como em Favorito, sem FK.
 */
@Entity
@Table(name = "reacao")
public class Reacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mensagem_id", nullable = false)
    private Long mensagemId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    // Lado de quem reagiu no match: "DOADOR" ou "ONG" (derivado do token).
    @Column(length = 10)
    private String lado;

    // Codigo da reacao (LIKE, LOVE, LAUGH, WOW, SAD, PRAY). Nunca o emoji cru.
    @Column(length = 20)
    private String emoji;

    private LocalDateTime dataCriacao;

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getMensagemId() { return mensagemId; }
    public void setMensagemId(Long mensagemId) { this.mensagemId = mensagemId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getLado() { return lado; }
    public void setLado(String lado) { this.lado = lado; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
