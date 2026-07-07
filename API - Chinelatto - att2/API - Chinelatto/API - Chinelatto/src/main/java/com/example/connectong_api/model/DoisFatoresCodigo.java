package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Codigo de verificacao em duas etapas (2FA) emitido no login quando a conta
 * tem doisFatores=1. Espelha o padrao do SenhaReset: codigo numerico de 6
 * digitos com validade de 10 minutos, guardado como texto (preserva zeros a
 * esquerda) e consumido por usadoEm (datetime; null = ainda valido) — em vez de
 * flag boolean, evitando o gotcha BIT/TINYINT do MySQL 5.6 com ddl-auto=validate.
 * Codigos anteriores do mesmo usuario sao invalidados (usadoEm preenchido)
 * sempre que um novo e gerado.
 */
@Entity
@Table(name = "dois_fatores_codigo")
public class DoisFatoresCodigo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    // Codigo numerico de 6 digitos (texto para preservar zeros a esquerda).
    @Column(length = 10)
    private String codigo;

    // Momento em que o codigo deixa de valer (criacao + 10 minutos).
    private LocalDateTime expiraEm;

    // null = codigo ainda valido; preenchido = ja usado OU invalidado por um
    // codigo mais novo.
    private LocalDateTime usadoEm;

    private LocalDateTime criadoEm;

    public DoisFatoresCodigo() {}

    @PrePersist
    public void aoCriar() {
        if (criadoEm == null) {
            criadoEm = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public LocalDateTime getExpiraEm() { return expiraEm; }
    public void setExpiraEm(LocalDateTime expiraEm) { this.expiraEm = expiraEm; }

    public LocalDateTime getUsadoEm() { return usadoEm; }
    public void setUsadoEm(LocalDateTime usadoEm) { this.usadoEm = usadoEm; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
}
