package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Bloqueio de um doador por uma ONG. Enquanto o par (ong, doador) existir aqui,
 * a ONG "some" para o doador: feed de necessidades/campanhas, busca de ONGs e
 * perfil publico completo deixam de aparecer, e o chat dos matches fica travado
 * nos dois sentidos (ver BloqueioService e os services de feed/mensagem).
 *
 * Unicidade por par garantida no banco (uq_bloqueio_ong_doador) e tratada como
 * IDEMPOTENTE no codigo: bloquear duas vezes nao duplica nem da erro.
 * criado_em = datetime (padrao do projeto: sem boolean no MySQL 5.6).
 */
@Entity
@Table(name = "bloqueio",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_bloqueio_ong_doador",
                columnNames = {"ong_id", "doador_id"}),
        indexes = @Index(name = "idx_bloqueio_doador", columnList = "doador_id"))
public class Bloqueio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ong_id", nullable = false)
    private Long ongId;

    @Column(name = "doador_id", nullable = false)
    private Long doadorId;

    @Column(name = "criado_em")
    private LocalDateTime criadoEm;

    public Bloqueio() {}

    public Bloqueio(Long ongId, Long doadorId) {
        this.ongId = ongId;
        this.doadorId = doadorId;
    }

    @PrePersist
    public void aoCriar() {
        if (this.criadoEm == null) {
            this.criadoEm = LocalDateTime.now();
        }
    }

    public Long getId() { return id; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }
}
