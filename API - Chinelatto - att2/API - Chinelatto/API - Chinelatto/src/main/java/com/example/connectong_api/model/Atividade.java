package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Evento do feed global de atividades da plataforma (Timeline).
 *
 * Registra de forma denormalizada o que aconteceu (ONG publicou necessidade,
 * alguem demonstrou interesse, campanha atingiu a meta, etc.) para alimentar
 * um feed publico que da "sensacao de atividade constante".
 *
 * Guarda apenas informacao adequada a um feed publico: nao expoe dados
 * sensiveis (valores de doacao individuais, conteudo de mensagens) nem
 * identifica doadores individualmente.
 */
@Entity
@Table(name = "atividade")
public class Atividade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tipo;        // NECESSIDADE, INTERESSE, PRESTACAO, CAMPANHA, DOACAO, AVALIACAO

    @Column(length = 500)
    private String descricao;   // texto pronto para exibir no feed

    private Long ongId;         // ONG relacionada (pode ser nula)

    private String ongNome;     // nome da ONG (snapshot para exibir sem join)

    private LocalDateTime dataCriacao;

    public Atividade() {}

    @PrePersist
    public void aoCriar() {
        this.dataCriacao = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Long getOngId() { return ongId; }
    public void setOngId(Long ongId) { this.ongId = ongId; }

    public String getOngNome() { return ongNome; }
    public void setOngNome(String ongNome) { this.ongNome = ongNome; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
}
