package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Foto do LOCAL de uma ONG (base64, MEDIUMTEXT). Maximo de 5 por ONG: o PUT do
 * perfil recebe a lista completa e SUBSTITUI as existentes (delete + insert).
 */
@Entity
@Table(name = "ong_foto")
public class OngFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ong_id", nullable = false)
    private Long ongId;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String foto;

    private LocalDateTime criadoEm;

    public OngFoto() {}

    @PrePersist
    public void aoCriar() {
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
}
