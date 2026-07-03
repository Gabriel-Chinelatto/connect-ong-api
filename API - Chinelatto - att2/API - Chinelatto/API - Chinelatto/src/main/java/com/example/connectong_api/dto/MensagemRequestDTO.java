package com.example.connectong_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para enviar uma mensagem no chat de um match.
 * O conteudo deixou de ser obrigatorio: uma mensagem pode ter texto vazio SE
 * tiver anexo (a regra "texto OU anexo" e validada no MensagemService).
 */
public class MensagemRequestDTO {

    @NotNull(message = "O interesseId e obrigatorio")
    @Positive(message = "O interesseId deve ser um numero positivo")
    private Long interesseId;

    // Mantido por compatibilidade com os clientes atuais (que sempre enviam),
    // mas o lado real e derivado do TOKEN no service (anti-spoofing).
    @Pattern(regexp = "DOADOR|ONG", message = "O remetente deve ser DOADOR ou ONG")
    private String remetente; // "DOADOR" ou "ONG"

    @Size(max = 2000, message = "A mensagem deve ter no maximo 2000 caracteres")
    private String conteudo;

    // Anexo (imagem) em base64 (opcional). Mesmo teto do PerfilDTO (~2.8MB).
    @Size(max = 3_000_000, message = "Anexo muito grande")
    private String anexoBase64;

    // Tipo do anexo; hoje apenas "imagem".
    @Pattern(regexp = "imagem", message = "anexoTipo deve ser 'imagem'")
    private String anexoTipo;

    public Long getInteresseId() { return interesseId; }
    public void setInteresseId(Long interesseId) { this.interesseId = interesseId; }

    public String getRemetente() { return remetente; }
    public void setRemetente(String remetente) { this.remetente = remetente; }

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }

    public String getAnexoBase64() { return anexoBase64; }
    public void setAnexoBase64(String anexoBase64) { this.anexoBase64 = anexoBase64; }

    public String getAnexoTipo() { return anexoTipo; }
    public void setAnexoTipo(String anexoTipo) { this.anexoTipo = anexoTipo; }
}
