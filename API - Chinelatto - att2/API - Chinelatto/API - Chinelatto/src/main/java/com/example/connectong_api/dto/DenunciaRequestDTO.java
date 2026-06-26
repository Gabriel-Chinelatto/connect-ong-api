package com.example.connectong_api.dto;

import jakarta.validation.constraints.*;

public class DenunciaRequestDTO {

    private Long denuncianteId;   // opcional (anonimo)

    @NotBlank(message = "O tipo do alvo e obrigatorio")
    @Size(max = 20)
    private String tipoAlvo;      // ONG | USUARIO | NECESSIDADE | CAMPANHA

    @NotNull(message = "O alvoId e obrigatorio")
    @Positive
    private Long alvoId;

    @NotBlank(message = "O motivo e obrigatorio")
    @Size(max = 60)
    private String motivo;

    @Size(max = 1000)
    private String descricao;

    public Long getDenuncianteId() { return denuncianteId; }
    public void setDenuncianteId(Long denuncianteId) { this.denuncianteId = denuncianteId; }

    public String getTipoAlvo() { return tipoAlvo; }
    public void setTipoAlvo(String tipoAlvo) { this.tipoAlvo = tipoAlvo; }

    public Long getAlvoId() { return alvoId; }
    public void setAlvoId(Long alvoId) { this.alvoId = alvoId; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
}
