package com.example.connectong_api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Perfil publico de uma ONG: dados de contato, descricao e sinais de confianca.
 * {@code verificada} (selo) e {@code cnpj} sustentam a verificacao; {@code notaMedia}
 * e {@code totalAvaliacoes} sao agregados denormalizados das avaliacoes, mantidos
 * aqui para exibir rapido sem recalcular (alimentam tambem o score de transparencia).
 * Os dados desta entidade sao publicos por natureza (a ONG quer ser encontrada).
 */
@Entity
@Table(name = "ong")
public class Ong {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String nome;

    @NotBlank
    @Email
    private String email;

    // Telefone e cidade sao OPCIONAIS no cadastro (o formulario da ONG e o
    // OngRegistroDTO os tratam como opcionais). Antes eram @NotBlank, o que fazia
    // o cadastro de uma ONG sem telefone/cidade falhar com erro de validacao de
    // entidade em tempo de persist. Mantemos so o limite de tamanho.
    @Size(max = 20)
    private String telefone;

    @Size(max = 50)
    private String cidade;

    // Descricao/"Sobre" institucional: ate 1000 chars (o SobreOngService gera
    // textos de ~400-600 chars pela IA; VARCHAR(1000) no banco via changeset
    // Liquibase ong-descricao-varchar-1000). Antes era 255 e cortava o texto.
    @Size(max = 1000)
    @Column(length = 1000)
    private String descricao;

    // Verificacao (selo de confianca)
    @Size(max = 20)
    private String cnpj;

    private Boolean verificada = false;

    // Avaliacoes (media denormalizada para exibir rapido)
    private Double notaMedia = 0.0;
    private Integer totalAvaliacoes = 0;

    @OneToMany(mappedBy = "ong")
    private List<Campanha> campanhas;

    // Soft-delete: null = ativa; preenchida = "excluida" (some das listagens,
    // ranking e perfil publico; historico preservado).
    private LocalDateTime dataExclusao;

    // ----- Perfil rico (feira) -----
    // Capa do perfil como base64 (MEDIUMTEXT, DTO limita ~2.8MB) — nunca URL.
    @Column(name = "capa_base64", columnDefinition = "MEDIUMTEXT")
    private String capaBase64;

    // Logo da ONG (a "foto de perfil" que aparece no circulo do cabecalho e no
    // avatar dos cards) como base64 — mesma regra da capa, nunca URL. E uma
    // imagem PEQUENA de proposito (~4 KB): ela viaja junto do perfil e, ao
    // contrario da capa, aparece em varias telas.
    @Column(name = "logo_base64", columnDefinition = "MEDIUMTEXT")
    private String logoBase64;

    @Size(max = 255)
    private String endereco;

    // Coordenadas do endereco (preenchidas quando a ONG escolhe o endereco no
    // autocomplete de mapa, no painel desktop). Permitem que o mapa do web
    // aponte o LOCAL EXATO da ONG (nao so o centro da cidade) e que o link do
    // Maps abra na coordenada certa. Sao OPCIONAIS: ONG antiga/sem coordenada
    // cai no fallback por cidade. Colunas adicionadas pelo changeset Liquibase
    // "feira-ong-lat-long".
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // ----- Streak do Top 1 do ranking de transparencia -----
    // top1Desde != null => esta ONG e a atual #1 (desde essa data).
    // ultimoReinadoDias = duracao (em dias) do ultimo reinado ja encerrado.
    @Column(name = "top1_desde")
    private LocalDateTime top1Desde;

    @Column(name = "ultimo_reinado_dias")
    private Integer ultimoReinadoDias;

    public Ong() {}

    public Ong(String nome, String email, String telefone, String cidade, String descricao) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.cidade = cidade;
        this.descricao = descricao;
    }

    public Long getId() { return id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCnpj() { return cnpj; }
    public void setCnpj(String cnpj) { this.cnpj = cnpj; }

    public Boolean getVerificada() { return verificada != null && verificada; }
    public void setVerificada(Boolean verificada) { this.verificada = verificada; }

    public Double getNotaMedia() { return notaMedia != null ? notaMedia : 0.0; }
    public void setNotaMedia(Double notaMedia) { this.notaMedia = notaMedia; }

    public Integer getTotalAvaliacoes() { return totalAvaliacoes != null ? totalAvaliacoes : 0; }
    public void setTotalAvaliacoes(Integer totalAvaliacoes) { this.totalAvaliacoes = totalAvaliacoes; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public LocalDateTime getDataExclusao() { return dataExclusao; }
    public void setDataExclusao(LocalDateTime dataExclusao) { this.dataExclusao = dataExclusao; }

    public String getCapaBase64() { return capaBase64; }
    public void setCapaBase64(String capaBase64) { this.capaBase64 = capaBase64; }

    public String getLogoBase64() { return logoBase64; }
    public void setLogoBase64(String logoBase64) { this.logoBase64 = logoBase64; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public LocalDateTime getTop1Desde() { return top1Desde; }
    public void setTop1Desde(LocalDateTime top1Desde) { this.top1Desde = top1Desde; }

    public Integer getUltimoReinadoDias() { return ultimoReinadoDias; }
    public void setUltimoReinadoDias(Integer ultimoReinadoDias) { this.ultimoReinadoDias = ultimoReinadoDias; }
}