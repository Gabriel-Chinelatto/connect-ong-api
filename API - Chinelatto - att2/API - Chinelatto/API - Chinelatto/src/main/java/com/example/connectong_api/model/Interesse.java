package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * O interesse de um doador em uma necessidade de uma ONG.
 * Quando a ONG aceita (status ACEITO), vira um "match" e habilita o chat.
 */
@Entity
@Table(name = "interesse")
public class Interesse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "necessidade_id")
    private Necessidade necessidade;

    @ManyToOne
    @JoinColumn(name = "doador_id")
    private Usuario doador;

    // PENDENTE (aguardando a ONG) -> ACEITO ou RECUSADO
    private String status;

    private LocalDateTime dataCriacao;

    public Interesse() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDENTE";
        }
    }

    public Long getId() { return id; }

    public Necessidade getNecessidade() { return necessidade; }
    public void setNecessidade(Necessidade necessidade) { this.necessidade = necessidade; }

    public Usuario getDoador() { return doador; }
    public void setDoador(Usuario doador) { this.doador = doador; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
