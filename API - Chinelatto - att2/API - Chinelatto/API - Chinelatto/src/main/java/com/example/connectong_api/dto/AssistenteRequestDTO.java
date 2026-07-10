package com.example.connectong_api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo de POST /assistente (chatbot que ajuda o DOADOR a decidir para quem doar).
 *
 * Contrato com os frontends:
 * {
 *   "mensagem": "tenho roupas pra doar em Limeira",   (obrigatorio)
 *   "historico": [ {"papel":"user|assistente","texto":"..."} ],  (opcional)
 *   "cidade": "Limeira",                               (opcional; se ausente e o
 *                                                       usuario estiver logado,
 *                                                       usamos a cidade do perfil)
 *   "imagemBase64": "data:image/jpeg;base64,...."      (opcional; foto do que a
 *                                                       pessoa quer doar — dispara
 *                                                       o modelo de VISAO da Groq)
 * }
 *
 * A mensagem tem tamanho limitado (@Size) para nao estourar tokens da IA nem
 * abrir espaco para abuso. O historico e truncado no service (ultimas ~6 trocas).
 * A imagem (quando enviada) NUNCA e persistida nem logada; so trafega para o
 * provedor de visao dentro da requisicao.
 */
public class AssistenteRequestDTO {

    @NotBlank(message = "A mensagem é obrigatória")
    @Size(max = 1000, message = "A mensagem é muito longa (máx. 1000 caracteres)")
    private String mensagem;

    // Historico da conversa (para dar contexto a IA). Opcional. Endpoint publico:
    // limitamos o TAMANHO da lista (o service so usa as ultimas ~6 trocas) e
    // validamos cada item em cascata (@Valid), para um chamador anonimo nao
    // enviar um historico gigante que pressiona memoria e tokens da IA.
    @Valid
    @Size(max = 20, message = "Histórico muito longo")
    private List<MensagemHistorico> historico;

    // Cidade do doador (opcional). Prioriza ONGs/necessidades proximas.
    @Size(max = 60, message = "Cidade muito longa")
    private String cidade;

    // Foto (opcional) do que a pessoa quer doar, para o modelo de VISAO da Groq.
    // Aceita data URL ("data:image/jpeg;base64,...") ou base64 puro. O teto de
    // ~6M caracteres comporta uma imagem de ate ~4MB depois do encode base64.
    // NUNCA e persistida nem logada.
    @Size(max = 6_000_000, message = "Imagem muito grande (máx. ~4MB)")
    private String imagemBase64;

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public List<MensagemHistorico> getHistorico() { return historico; }
    public void setHistorico(List<MensagemHistorico> historico) { this.historico = historico; }

    public String getCidade() { return cidade; }
    public void setCidade(String cidade) { this.cidade = cidade; }

    public String getImagemBase64() { return imagemBase64; }
    public void setImagemBase64(String imagemBase64) { this.imagemBase64 = imagemBase64; }

    /** Uma troca do historico: papel = "user" ou "assistente"; texto = conteudo. */
    public static class MensagemHistorico {
        @Size(max = 20, message = "Papel inválido")
        private String papel;

        @Size(max = 1000, message = "Mensagem do histórico muito longa")
        private String texto;

        public MensagemHistorico() {}

        public MensagemHistorico(String papel, String texto) {
            this.papel = papel;
            this.texto = texto;
        }

        public String getPapel() { return papel; }
        public void setPapel(String papel) { this.papel = papel; }

        public String getTexto() { return texto; }
        public void setTexto(String texto) { this.texto = texto; }
    }
}
