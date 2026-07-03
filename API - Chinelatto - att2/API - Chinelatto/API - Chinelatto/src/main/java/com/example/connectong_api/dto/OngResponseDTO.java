package com.example.connectong_api.dto;

import java.util.List;

public class OngResponseDTO {

    private Long id;

    private String nome;

    private String email;

    private String telefone;

    private String cidade;

    private String descricao;

    private String cnpj;

    private Boolean verificada;

    private Double notaMedia;

    private Integer totalAvaliacoes;

    // Perfil rico (preenchidos no GET /ongs/{id} e no PUT; ficam null na
    // listagem para nao inflar a resposta com megabytes de base64).
    private String capaBase64;
    private String endereco;
    private List<String> fotosLocal;

    public OngResponseDTO(
            Long id,
            String nome,
            String email,
            String telefone,
            String cidade,
            String descricao,
            String cnpj,
            Boolean verificada,
            Double notaMedia,
            Integer totalAvaliacoes
    ) {

        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.cidade = cidade;
        this.descricao = descricao;
        this.cnpj = cnpj;
        this.verificada = verificada;
        this.notaMedia = notaMedia;
        this.totalAvaliacoes = totalAvaliacoes;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getCidade() {
        return cidade;
    }

    public String getDescricao() {
        return descricao;
    }

    public String getCnpj() {
        return cnpj;
    }

    public Boolean getVerificada() {
        return verificada;
    }

    public Double getNotaMedia() {
        return notaMedia;
    }

    public Integer getTotalAvaliacoes() {
        return totalAvaliacoes;
    }

    public String getCapaBase64() { return capaBase64; }
    public void setCapaBase64(String capaBase64) { this.capaBase64 = capaBase64; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public List<String> getFotosLocal() { return fotosLocal; }
    public void setFotosLocal(List<String> fotosLocal) { this.fotosLocal = fotosLocal; }
}
