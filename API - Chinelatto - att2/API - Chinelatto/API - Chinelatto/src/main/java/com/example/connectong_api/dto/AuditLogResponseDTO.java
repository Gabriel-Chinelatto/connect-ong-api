package com.example.connectong_api.dto;

import com.example.connectong_api.model.AuditLog;

import java.time.LocalDateTime;

public class AuditLogResponseDTO {

    private Long id;
    private String acao;
    private Long usuarioId;
    private String descricao;
    private String ip;
    private LocalDateTime criadoEm;

    public AuditLogResponseDTO(AuditLog l) {
        this.id = l.getId();
        this.acao = l.getAcao();
        this.usuarioId = l.getUsuarioId();
        this.descricao = l.getDescricao();
        this.ip = l.getIp();
        this.criadoEm = l.getCriadoEm();
    }

    public Long getId() { return id; }
    public String getAcao() { return acao; }
    public Long getUsuarioId() { return usuarioId; }
    public String getDescricao() { return descricao; }
    public String getIp() { return ip; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
}
