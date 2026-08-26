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
    // Teto de ITENS: so um limite contra abuso. Os apps mandam as ultimas 8
    // trocas e o service usa as ultimas 6 — 40 e folga larga. Estava em 20, o
    // que rejeitava (400) uma conversa de mais de 10 perguntas caso o cliente
    // mandasse tudo. Mesma armadilha do limite por item: aqui o excesso deve
    // ser IGNORADO pelo service, nunca derrubar a conversa.
    @Valid
    @Size(max = 40, message = "Histórico muito longo")
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
        /**
         * Teto de caracteres que cada troca leva para o prompt. O historico serve
         * para dar CONTEXTO, nao para reenviar a conversa inteira: o comeco de
         * uma resposta ja diz do que ela tratava.
         *
         * O valor tambem protege a COTA da IA. A Groq gratuita da 8.000 tokens
         * por minuto por modelo; como o historico viaja em TODA pergunta, cada
         * caractere aqui e pago repetidamente. Com 6 trocas de 700, o historico
         * custa ~1.000 tokens por pergunta em vez de ~1.800.
         */
        public static final int LIMITE_PARA_IA = 700;

        @Size(max = 20, message = "Papel inválido")
        private String papel;

        // ATENCAO ao mexer neste limite: ele ja quebrou o chat.
        // Estava em 1.000 — mas a RESPOSTA do assistente costuma ter 1.500 a
        // 2.500 caracteres. Como o front devolve o historico a cada pergunta, a
        // resposta anterior estourava a validacao, a API devolvia 400 e a tela
        // mostrava "O assistente esta indisponivel no momento". Na pratica: o
        // chat respondia as primeiras perguntas e morria em seguida, parecendo
        // falha da IA. Aqui o valor e so um TETO CONTRA ABUSO; quem limita o
        // que vai para a IA e textoParaIa(), que CORTA em vez de recusar.
        @Size(max = 20_000, message = "Mensagem do histórico muito longa")
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

        /**
         * O texto desta troca pronto para o prompt: sem espacos nas pontas e
         * cortado em {@value #LIMITE_PARA_IA} caracteres. Cortar (em vez de
         * recusar a requisicao) e o que garante que uma conversa longa nunca
         * derrube o assistente. Devolve null quando nao ha o que aproveitar.
         */
        public String textoParaIa() {
            if (texto == null || texto.isBlank()) {
                return null;
            }
            String limpo = texto.trim();
            return limpo.length() <= LIMITE_PARA_IA
                    ? limpo
                    : limpo.substring(0, LIMITE_PARA_IA) + "…";
        }
    }
}
