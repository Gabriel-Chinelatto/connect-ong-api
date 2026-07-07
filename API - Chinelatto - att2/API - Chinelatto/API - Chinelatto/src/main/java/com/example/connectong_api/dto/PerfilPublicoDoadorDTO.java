package com.example.connectong_api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Perfil PUBLICO de um DOADOR (endpoint sem login). PRIVACIDADE: nunca expoe
 * valores em R$ (as doacoes PIX aparecem apenas como CONTAGEM em
 * stats.totalDoacoesPix). email e telefone so aparecem quando o proprio doador
 * LIGOU os toggles mostrarEmail/mostrarTelefone nas preferencias — espelhando o
 * que ja vale para o perfil publico da ONG. Com o toggle desligado (ou sem
 * preferencia salva) o campo vai null e, por @JsonInclude(NON_NULL) nesses dois
 * campos, e OMITIDO do JSON (o frontend le ausente = nao divulgado).
 */
public class PerfilPublicoDoadorDTO {

    private Long id;
    private String nome;
    private String cidade;
    private String estado;
    private String fotoBase64;

    // Contato: so preenchidos quando o toggle de privacidade correspondente
    // estiver ligado (senao null, e o NON_NULL abaixo omite o campo). Nunca
    // vazam por padrao.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String telefone;

    // Data de criacao da conta (null para contas anteriores a coluna).
    private LocalDateTime membroDesde;

    private Double notaMediaDoador;
    private Integer totalAvaliacoesDoador;

    private Stats stats;
    private List<AvaliacaoDoadorResponseDTO> avaliacoes;
    private List<PrestacaoRecebidaDTO> prestacoesRecebidas;

    public PerfilPublicoDoadorDTO(Long id, String nome, String cidade, String estado,
                                  String fotoBase64, String email, String telefone,
                                  LocalDateTime membroDesde,
                                  Double notaMediaDoador, Integer totalAvaliacoesDoador,
                                  Stats stats,
                                  List<AvaliacaoDoadorResponseDTO> avaliacoes,
                                  List<PrestacaoRecebidaDTO> prestacoesRecebidas) {
        this.id = id;
        this.nome = nome;
        this.cidade = cidade;
        this.estado = estado;
        this.fotoBase64 = fotoBase64;
        this.email = email;
        this.telefone = telefone;
        this.membroDesde = membroDesde;
        this.notaMediaDoador = notaMediaDoador;
        this.totalAvaliacoesDoador = totalAvaliacoesDoador;
        this.stats = stats;
        this.avaliacoes = avaliacoes;
        this.prestacoesRecebidas = prestacoesRecebidas;
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getCidade() { return cidade; }
    public String getEstado() { return estado; }
    public String getFotoBase64() { return fotoBase64; }
    public String getEmail() { return email; }
    public String getTelefone() { return telefone; }
    public LocalDateTime getMembroDesde() { return membroDesde; }
    public Double getNotaMediaDoador() { return notaMediaDoador; }
    public Integer getTotalAvaliacoesDoador() { return totalAvaliacoesDoador; }
    public Stats getStats() { return stats; }
    public List<AvaliacaoDoadorResponseDTO> getAvaliacoes() { return avaliacoes; }
    public List<PrestacaoRecebidaDTO> getPrestacoesRecebidas() { return prestacoesRecebidas; }

    /** Numeros publicos do doador (contagens, nunca valores em R$). */
    public static class Stats {
        private long matchesConcluidos;
        private long totalDoacoesPix;

        public Stats(long matchesConcluidos, long totalDoacoesPix) {
            this.matchesConcluidos = matchesConcluidos;
            this.totalDoacoesPix = totalDoacoesPix;
        }

        public long getMatchesConcluidos() { return matchesConcluidos; }
        public long getTotalDoacoesPix() { return totalDoacoesPix; }
    }

    /** Uma prestacao de contas recebida pelo doador (resumo publico). */
    public static class PrestacaoRecebidaDTO {
        private String titulo;
        private String descricao;
        private String ongNome;
        private String necessidadeTitulo;
        private LocalDateTime criadoEm;

        public PrestacaoRecebidaDTO(String titulo, String descricao, String ongNome,
                                    String necessidadeTitulo, LocalDateTime criadoEm) {
            this.titulo = titulo;
            this.descricao = descricao;
            this.ongNome = ongNome;
            this.necessidadeTitulo = necessidadeTitulo;
            this.criadoEm = criadoEm;
        }

        public String getTitulo() { return titulo; }
        public String getDescricao() { return descricao; }
        public String getOngNome() { return ongNome; }
        public String getNecessidadeTitulo() { return necessidadeTitulo; }
        public LocalDateTime getCriadoEm() { return criadoEm; }
    }
}
