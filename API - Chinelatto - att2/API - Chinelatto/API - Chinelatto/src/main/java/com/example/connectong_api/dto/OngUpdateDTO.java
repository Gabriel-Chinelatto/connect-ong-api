package com.example.connectong_api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Dados de entrada do PUT /ongs/{id}. Substitui o bind direto da entidade Ong
 * para aceitar tambem os campos do perfil rico: capa (base64), endereco e as
 * fotos do local (lista que SUBSTITUI as existentes; max 5).
 */
public class OngUpdateDTO {

    @Size(max = 100)
    private String nome;

    private String email;

    @Size(max = 20)
    private String telefone;

    @Size(max = 50)
    private String cidade;

    // Ate 1000 chars: acomoda o "Sobre" gerado pela IA (SobreOngService, ~600).
    @Size(max = 1000)
    private String descricao;

    // Logo (foto de perfil) em base64. So sobrescreve quando enviado
    // (null = mantem o atual). Teto MENOR que o da capa de proposito: o logo
    // aparece em varias telas e precisa ser leve.
    @Size(max = 400_000, message = "Logo muito grande")
    private String logoBase64;

    // Capa em base64 (mesmo teto ~2.8MB do PerfilDTO). So sobrescreve quando
    // enviada (null = mantem a atual).
    @Size(max = 3_000_000, message = "Imagem muito grande")
    private String capaBase64;

    @Size(max = 255)
    private String endereco;

    // Coordenadas do endereco escolhido no autocomplete de mapa (opcionais).
    // So sobrescrevem quando enviadas (null = mantem as atuais). Validadas em
    // faixa geografica valida no service.
    private Double latitude;
    private Double longitude;

    // Fotos do local: quando presente, SUBSTITUI todas as existentes.
    @Size(max = 5, message = "No maximo 5 fotos do local")
    private List<@Size(max = 3_000_000, message = "Imagem muito grande") String> fotosLocal;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getTelefone() { return telefone; }
    public void setTelefone(String telefone) { this.telefone = telefone; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getLogoBase64() { return logoBase64; }
    public void setLogoBase64(String logoBase64) { this.logoBase64 = logoBase64; }

    public String getCapaBase64() { return capaBase64; }
    public void setCapaBase64(String capaBase64) { this.capaBase64 = capaBase64; }

    public String getEndereco() { return endereco; }
    public void setEndereco(String endereco) { this.endereco = endereco; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public List<String> getFotosLocal() { return fotosLocal; }
    public void setFotosLocal(List<String> fotosLocal) { this.fotosLocal = fotosLocal; }
}
