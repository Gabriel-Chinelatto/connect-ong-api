package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Registro de consentimento (LGPD, item F-04). Cada aceite dos Termos de Uso e
 * da Politica de Privacidade vira uma linha com a VERSAO do documento, a data e
 * o IP: e a prova de que o titular consentiu (LGPD art. 8, par. 2 — o onus da
 * prova e de quem trata os dados). Quando o texto dos documentos mudar, sobe a
 * {@link #VERSAO_ATUAL} e o historico mostra quem aceitou qual versao.
 */
@Entity
@Table(name = "consentimento")
public class Consentimento {

    /** Termos de Uso + Politica de Privacidade, aceitos juntos no cadastro. */
    public static final String DOCUMENTO_TERMOS = "TERMOS_E_PRIVACIDADE";

    /** Versao vigente dos documentos legais (data da ultima revisao). */
    public static final String VERSAO_ATUAL = "2026-09-22";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 40)
    private String documento;

    @Column(nullable = false, length = 20)
    private String versao;

    @Column(nullable = false)
    private LocalDateTime aceitoEm;

    @Column(length = 64)
    private String ip;

    public Consentimento() {}

    public Consentimento(Long usuarioId, String documento, String versao, String ip) {
        this.usuarioId = usuarioId;
        this.documento = documento;
        this.versao = versao;
        this.ip = ip;
    }

    @PrePersist
    public void aoCriar() {
        if (this.aceitoEm == null) this.aceitoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getDocumento() { return documento; }
    public String getVersao() { return versao; }
    public LocalDateTime getAceitoEm() { return aceitoEm; }
    public String getIp() { return ip; }
}
