package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Codigo de recuperacao de senha ("esqueci a senha").
 *
 * Cada solicitacao gera um codigo numerico de 6 digitos com validade de 15
 * minutos. O consumo e marcado por usadoEm (datetime; null = ainda valido),
 * seguindo o padrao do projeto de NAO usar boolean em colunas novas (o
 * Liquibase criaria TINYINT e o Hibernate em modo validate espera BIT no
 * MySQL 5.6). Codigos antigos do mesmo usuario sao invalidados (usadoEm
 * preenchido) sempre que um novo e gerado.
 */
@Entity
@Table(name = "senha_reset")
public class SenhaReset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    // Codigo numerico de 6 digitos (guardado como texto para preservar zeros
    // a esquerda, ex.: "042317").
    @Column(length = 10)
    private String codigo;

    // Momento em que o codigo deixa de valer (criacao + 15 minutos).
    private LocalDateTime expiraEm;

    // null = codigo ainda valido; preenchido = ja usado OU invalidado por um
    // codigo mais novo (padrao datetime-em-vez-de-flag do projeto).
    private LocalDateTime usadoEm;

    private LocalDateTime criadoEm;

    public SenhaReset() {}

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
