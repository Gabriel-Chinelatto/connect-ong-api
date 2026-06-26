package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Denuncia de conteudo (ONG, usuario, necessidade ou campanha) feita por um
 * doador. Apoio a credibilidade/moderacao. Sem UI de admin ainda: a equipe
 * consulta via REST/Swagger e resolve.
 */
@Entity
@Table(name = "denuncia")
public class Denuncia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long denuncianteId;   // quem denunciou (pode ser nulo = anonimo)

    private String tipoAlvo;      // ONG | USUARIO | NECESSIDADE | CAMPANHA

    private Long alvoId;          // id do alvo denunciado

    private String motivo;        // categoria curta (ex.: SPAM, FRAUDE, ABUSO)

    @Column(length = 1000)
    private String descricao;     // detalhes

    private String status;        // ABERTA | RESOLVIDA

    private LocalDateTime dataCriacao;

    public Denuncia() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
        if (this.status == null) this.status = "ABERTA";
    }

    public Long getId() { return id; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
