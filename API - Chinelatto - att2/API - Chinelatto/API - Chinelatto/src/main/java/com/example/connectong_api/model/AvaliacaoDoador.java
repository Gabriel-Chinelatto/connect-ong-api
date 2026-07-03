package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Avaliacao que uma ONG da a um DOADOR (nota 1-5 + comentario) — o espelho da
 * Avaliacao (doador -> ONG). Uma unica avaliacao por par ong+doador (upsert no
 * service). Os agregados (nota media/total) ficam denormalizados no Usuario.
 */
@Entity
@Table(name = "avaliacao_doador")
public class AvaliacaoDoador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "doador_id", nullable = false)
    private Long doadorId;

    @Column(name = "ong_id", nullable = false)
    private Long ongId;

    private Integer nota; // 1 a 5

    @Column(length = 500)
    private String comentario;

    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;

    public AvaliacaoDoador() {}

    @PrePersist
    public void aoCriar() {
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    @PreUpdate
    public void aoAtualizar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public Integer getNota() { return nota; }
    public void setNota(Integer nota) { this.nota = nota; }

    public String getComentario() { return comentario; }
    public void setComentario(String comentario) { this.comentario = comentario; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public LocalDateTime getAtualizadoEm() { return atualizadoEm; }
}
