package com.example.connectong_api.dto;

import java.time.LocalDateTime;

/**
 * Situacao do OUTRO participante do chat, do ponto de vista de quem consulta:
 * - online: teve atividade recente no chat, CALCULADO NO SERVIDOR (janela de
 *   2 minutos desde o ultimo heartbeat) — o cliente nao precisa comparar horas;
 * - ultimoVisto: momento da ultima atividade (LEGADO: LocalDateTime sem fuso,
 *   mantido por compatibilidade — clientes antigos interpretavam errado o fuso);
 * - ultimoVistoEpoch: o MESMO instante em milissegundos desde a epoch (UTC),
 *   a prova de fuso — os clientes novos devem usar este campo;
 * - digitando: esta digitando agora (heartbeat efemero em memoria).
 */
public class ChatStatusDTO {

    private boolean online;
    private LocalDateTime ultimoVisto;
    private Long ultimoVistoEpoch;
    private boolean digitando;

    public ChatStatusDTO(boolean online, LocalDateTime ultimoVisto,
                         Long ultimoVistoEpoch, boolean digitando) {
        this.online = online;
        this.ultimoVisto = ultimoVisto;
        this.ultimoVistoEpoch = ultimoVistoEpoch;
        this.digitando = digitando;
    }

    public boolean isOnline() { return online; }
    public LocalDateTime getUltimoVisto() { return ultimoVisto; }
    public Long getUltimoVistoEpoch() { return ultimoVistoEpoch; }
    public boolean isDigitando() { return digitando; }
}
