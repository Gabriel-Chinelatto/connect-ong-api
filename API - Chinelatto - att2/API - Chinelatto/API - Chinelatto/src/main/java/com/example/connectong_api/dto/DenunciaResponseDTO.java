package com.example.connectong_api.dto;

import com.example.connectong_api.model.Denuncia;

import java.time.LocalDateTime;

public class DenunciaResponseDTO {

    private Long id;
    // denuncianteId NAO e exposto: a denuncia e anonima para a moderacao (antes
    // vazava a identidade de quem reportou a quem consultasse a lista).
    private String tipoAlvo;
    private Long alvoId;
    private String motivo;
    private String descricao;
    private String status;
    private LocalDateTime dataCriacao;

    public DenunciaResponseDTO(Denuncia d) {
        this.id = d.getId();
        this.tipoAlvo = d.getTipoAlvo();
        this.alvoId = d.getAlvoId();
        this.motivo = d.getMotivo();
        this.descricao = d.getDescricao();
        this.status = d.getStatus();
        this.dataCriacao = d.getDataCriacao();
    }

    public Long getId() { return id; }
    public String getTipoAlvo() { return tipoAlvo; }
    public Long getAlvoId() { return alvoId; }
    public String getMotivo() { return motivo; }
    public String getDescricao() { return descricao; }
    public String getStatus() { return status; }
    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
