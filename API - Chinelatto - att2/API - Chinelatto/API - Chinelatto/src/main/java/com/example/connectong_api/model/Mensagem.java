package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Uma mensagem de chat trocada dentro de um match (Interesse ACEITO).
 * remetente = "DOADOR" ou "ONG" (quem enviou).
 */
@Entity
@Table(name = "mensagem")
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "interesse_id")
    private Interesse interesse;

    private String remetente;

    @Column(length = 1000)
    private String conteudo;

    private LocalDateTime dataEnvio;

    public Mensagem() {}

    @PrePersist
    public void aoCriar() {
        this.dataEnvio = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Interesse getInteresse() { return interesse; }
    public void setInteresse(Interesse interesse) { this.interesse = interesse; }

    public String getRemetente() { return remetente; }
    public void setRemetente(String remetente) { this.remetente = remetente; }

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }

    public LocalDateTime getDataEnvio() { return dataEnvio; }
}
