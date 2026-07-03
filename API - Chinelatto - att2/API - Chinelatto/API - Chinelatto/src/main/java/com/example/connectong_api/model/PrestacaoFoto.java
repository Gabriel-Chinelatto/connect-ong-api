package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Uma foto (base64) de uma prestacao de contas. Tabela separada porque uma
 * prestacao pode ter ate 5 fotos (limite validado no DTO/service). O conteudo
 * fica em MEDIUMTEXT (base64, DTO limita a ~2.8MB) — nunca URL externa.
 */
@Entity
@Table(name = "prestacao_foto")
public class PrestacaoFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Id simples (sem @ManyToOne): a foto so e lida a partir da prestacao,
    // nunca navegada no sentido contrario.
    @Column(name = "prestacao_id", nullable = false)
    private Long prestacaoId;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String foto;

    private LocalDateTime criadoEm;

    public PrestacaoFoto() {}

    @PrePersist
    public void aoCriar() {
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getPrestacaoId() { return prestacaoId; }
    public void setPrestacaoId(Long prestacaoId) { this.prestacaoId = prestacaoId; }

    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
}
