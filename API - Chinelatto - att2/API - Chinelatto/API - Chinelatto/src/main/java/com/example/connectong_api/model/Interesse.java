package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * O interesse de um doador em uma necessidade de uma ONG.
 * Quando a ONG aceita (status ACEITO), vira um "match" e habilita o chat.
 */
@Entity
@Table(name = "interesse")
public class Interesse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "necessidade_id")
    private Necessidade necessidade;

    @ManyToOne
    @JoinColumn(name = "doador_id")
    private Usuario doador;

    // PENDENTE (aguardando a ONG) -> ACEITO ou RECUSADO; ACEITO -> CONCLUIDO
    // (a ONG confirma que recebeu a doacao fisicamente).
    private String status;

    private LocalDateTime dataCriacao;

    // Momento em que a ONG marcou o match como CONCLUIDO (doacao recebida).
    // Base do prazo de 10 dias da prestacao de contas (ver PrestacaoService).
    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;

    // Momento da ULTIMA mudanca de status (aceite/recusa/conclusao). Permite ao
    // painel mostrar "recusado em ..." / "aceito em ...". Null enquanto PENDENTE.
    @Column(name = "data_status")
    private LocalDateTime dataStatus;

    public Interesse() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
        if (this.status == null || this.status.isBlank()) {
            this.status = "PENDENTE";
        }
    }

    public Long getId() { return id; }

    public Necessidade getNecessidade() { return necessidade; }
    public void setNecessidade(Necessidade necessidade) { this.necessidade = necessidade; }

    public Usuario getDoador() { return doador; }
    public void setDoador(Usuario doador) { this.doador = doador; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    // Setter usado em testes para simular um interesse antigo (dias de espera).
    // No fluxo real a dataCriacao e definida uma vez no @PrePersist.
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }

    public LocalDateTime getDataConclusao() { return dataConclusao; }
    public void setDataConclusao(LocalDateTime dataConclusao) { this.dataConclusao = dataConclusao; }

    public LocalDateTime getDataStatus() { return dataStatus; }
    public void setDataStatus(LocalDateTime dataStatus) { this.dataStatus = dataStatus; }
}
