package com.example.connectong_api.dto;

import java.util.List;
import java.time.LocalDateTime;

/**
 * Dados de saida de uma mensagem do chat.
 */
public class MensagemResponseDTO {

    private Long id;
    private Long interesseId;
    private String remetente;
    private String conteudo;
    private LocalDateTime dataEnvio;
    // "visto": true quando o outro participante ja leu (data_leitura preenchida).
    // O cliente mostra 1 check (enviada) ou 2 checks (lida) nas mensagens proprias.
    private boolean lida;
    // Reacoes (emoji) desta mensagem: 0..2 num chat de 2 participantes.
    private List<ReacaoDTO> reacoes;

    // Anexo (imagem) em base64 e seu tipo ("imagem"); null quando nao ha anexo.
    private String anexoBase64;
    private String anexoTipo;

    public MensagemResponseDTO(
            Long id,
            Long interesseId,
            String remetente,
            String conteudo,
            LocalDateTime dataEnvio,
            boolean lida,
            List<ReacaoDTO> reacoes,
            String anexoBase64,
            String anexoTipo
    ) {
        this.id = id;
        this.interesseId = interesseId;
        this.remetente = remetente;
        this.conteudo = conteudo;
        this.dataEnvio = dataEnvio;
        this.lida = lida;
        this.reacoes = reacoes;
        this.anexoBase64 = anexoBase64;
        this.anexoTipo = anexoTipo;
    }

    public Long getId() { return id; }
    public Long getInteresseId() { return interesseId; }
    public String getRemetente() { return remetente; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getDataEnvio() { return dataEnvio; }
    public boolean isLida() { return lida; }
    public List<ReacaoDTO> getReacoes() { return reacoes; }
    public String getAnexoBase64() { return anexoBase64; }
    public String getAnexoTipo() { return anexoTipo; }
}
