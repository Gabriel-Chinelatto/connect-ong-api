package com.example.connectong_api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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

    @NotBlank
    @Size(max = 20)
    private String telefone;

    @NotBlank
    @Size(max = 50)
    private String cidade;

    @Size(max = 255)
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
}