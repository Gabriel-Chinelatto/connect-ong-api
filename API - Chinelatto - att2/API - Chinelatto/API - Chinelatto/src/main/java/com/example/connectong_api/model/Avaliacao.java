package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Avaliacao de uma ONG feita por um doador (nota de 1 a 5 + comentario).
 */
@Entity
@Table(name = "avaliacao")
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ongId;
    private Long doadorId;
    private String doadorNome;

    private Integer nota; // 1 a 5

    @Column(length = 500)
    private String comentario;

    private LocalDateTime dataCriacao;

    public Avaliacao() {}

    // So no INSERT: a data de criacao nao deve ser sobrescrita a cada update.
    // (Antes havia @PreUpdate aqui, que regravava dataCriacao em toda alteracao.)
    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public String getDoadorNome() { return doadorNome; }
    public void setDoadorNome(String doadorNome) { this.doadorNome = doadorNome; }

    public Integer getNota() { return nota; }
    public void setNota(Integer nota) { this.nota = nota; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
