package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Doacao financeira (PIX) de um doador para uma ONG.
 * SIMULADA — sem gateway real (TCC). Gera um codigo/comprovante.
 */
@Entity
@Table(name = "doacao_financeira")
public class DoacaoFinanceira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ongId;
    private String ongNome;
    private Long doadorId;
    private String doadorNome;

    private Double valor;

    private String codigoPix;   // codigo "copia e cola" simulado
    private String status;      // CONFIRMADO (simulado)

    private LocalDateTime dataCriacao;

    public DoacaoFinanceira() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
        if (this.status == null) this.status = "CONFIRMADO";
    }

    public Long getId() { return id; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public String getOngNome() { return ongNome; }
    public void setOngNome(String ongNome) { this.ongNome = ongNome; }

    public Long getDoadorId() { return doadorId; }
    public void setDoadorId(Long doadorId) { this.doadorId = doadorId; }

    public String getDoadorNome() { return doadorNome; }
    public void setDoadorNome(String doadorNome) { this.doadorNome = doadorNome; }

    public Double getValor() { return valor; }
    public void setValor(Double valor) { this.valor = valor; }

    public String getCodigoPix() { return codigoPix; }
    public void setCodigoPix(String codigoPix) { this.codigoPix = codigoPix; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
