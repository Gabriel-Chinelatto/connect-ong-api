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

    public MensagemResponseDTO(
            Long id,
            Long interesseId,
            String remetente,
            String conteudo,
            LocalDateTime dataEnvio,
            boolean lida,
            List<ReacaoDTO> reacoes
    ) {
        this.id = id;
        this.interesseId = interesseId;
        this.remetente = remetente;
        this.conteudo = conteudo;
        this.dataEnvio = dataEnvio;
        this.lida = lida;
        this.reacoes = reacoes;
    }

    public Long getId() { return id; }
    public Long getInteresseId() { return interesseId; }
    public String getRemetente() { return remetente; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getDataEnvio() { return dataEnvio; }
    public boolean isLida() { return lida; }
    public List<ReacaoDTO> getReacoes() { return reacoes; }
}
