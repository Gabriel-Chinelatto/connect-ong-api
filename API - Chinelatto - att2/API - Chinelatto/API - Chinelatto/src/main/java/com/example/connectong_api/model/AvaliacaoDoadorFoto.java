package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Uma foto (base64) que a ONG anexa ao avaliar um DOADOR — normalmente a foto
 * da doacao/produto recebido, que da lastro visual a nota. Tabela separada
 * (mesmo padrao de PrestacaoFoto): uma avaliacao pode ter algumas fotos.
 * Conteudo em MEDIUMTEXT (base64; o service limita a quantidade e o tamanho) —
 * nunca URL externa.
 */
@Entity
@Table(name = "avaliacao_doador_foto")
public class AvaliacaoDoadorFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Id simples (sem @ManyToOne): a foto so e lida a partir da avaliacao.
    @Column(name = "avaliacao_doador_id", nullable = false)
    private Long avaliacaoDoadorId;

    @Column(columnDefinition = "MEDIUMTEXT")
    private String foto;

    private LocalDateTime criadoEm;

    public AvaliacaoDoadorFoto() {}

    @PrePersist
    public void aoCriar() {
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Long getAvaliacaoDoadorId() { return avaliacaoDoadorId; }
    public void setAvaliacaoDoadorId(Long avaliacaoDoadorId) {
        this.avaliacaoDoadorId = avaliacaoDoadorId;
    }

    public String getFoto() { return foto; }
    public void setFoto(String foto) { this.foto = foto; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
}
