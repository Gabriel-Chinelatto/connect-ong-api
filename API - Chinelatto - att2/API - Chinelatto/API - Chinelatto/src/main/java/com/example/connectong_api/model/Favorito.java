package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Favorito de um doador: marca uma ONG ou uma campanha como favorita, para
 * acompanhar de perto. Unicidade (usuario + tipo + alvo) garantida no service.
 */
@Entity
@Table(name = "favorito")
public class Favorito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;   // doador dono do favorito

    private String tipo;      // ONG | CAMPANHA

    private Long alvoId;      // id da ONG ou da campanha

    private LocalDateTime dataCriacao;

    public Favorito() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public Long getAlvoId() { return alvoId; }
    public void setAlvoId(Long alvoId) { this.alvoId = alvoId; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
