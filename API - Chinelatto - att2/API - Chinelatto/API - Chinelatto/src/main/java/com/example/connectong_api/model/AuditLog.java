package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro de auditoria: guarda eventos relevantes de seguranca
 * (logins, cadastros, doacoes) para rastreabilidade.
 *
 * Nunca armazena dados sensiveis (senha, token); apenas o que aconteceu,
 * quem (usuarioId, quando conhecido), de onde (ip) e uma descricao curta.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String acao;        // ex.: LOGIN_SUCESSO, LOGIN_FALHA, CADASTRO_USUARIO

    private Long usuarioId;     // pode ser nulo (ex.: login que falhou)

    @Column(length = 500)
    private String descricao;   // texto curto e seguro (sem dados sensiveis)

    private String ip;          // endereco de origem da requisicao

    private LocalDateTime criadoEm;

    public AuditLog() {}

    @PrePersist
    public void aoCriar() {
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getAcao() { return acao; }
    public void setAcao(String acao) { this.acao = acao; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
}
