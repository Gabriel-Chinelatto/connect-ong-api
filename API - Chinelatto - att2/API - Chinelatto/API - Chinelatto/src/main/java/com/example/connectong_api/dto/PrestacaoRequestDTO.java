package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Dados de entrada para a ONG publicar uma prestacao de contas num match.
 */
public class PrestacaoRequestDTO {

    @NotNull(message = "O interesseId e obrigatorio")
    @Positive(message = "O interesseId deve ser um numero positivo")
    private Long interesseId;

    @NotBlank(message = "O titulo e obrigatorio")
    @Size(max = 150, message = "O titulo deve ter no maximo 150 caracteres")
    private String titulo;

    @NotBlank(message = "A descricao e obrigatoria")
    @Size(max = 2000, message = "A descricao deve ter no maximo 2000 caracteres")
    private String descricao;

    @Size(max = 500, message = "A URL da foto e muito longa")
    private String fotoUrl;

    // Fotos embutidas em base64 (opcional): no maximo 5, cada uma limitada a
    // ~2.8MB de string (mesmo teto do PerfilDTO, evita DoS de memoria/banco).
    @Size(max = 5, message = "No maximo 5 fotos por prestacao")
    private List<@Size(max = 3_000_000, message = "Imagem muito grande") String> fotos;

    // Valor (R$) utilizado nesta prestacao (opcional; transparencia).
    @Positive(message = "O valorUtilizado deve ser maior que zero")
    private Double valorUtilizado;

    public Long getInteresseId() { return interesseId; }
    public void setInteresseId(Long interesseId) { this.interesseId = interesseId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getFotoUrl() { return fotoUrl; }
    public void setFotoUrl(String fotoUrl) { this.fotoUrl = fotoUrl; }

    public List<String> getFotos() { return fotos; }
    public void setFotos(List<String> fotos) { this.fotos = fotos; }

    public Double getValorUtilizado() { return valorUtilizado; }
    public void setValorUtilizado(Double valorUtilizado) { this.valorUtilizado = valorUtilizado; }
}
