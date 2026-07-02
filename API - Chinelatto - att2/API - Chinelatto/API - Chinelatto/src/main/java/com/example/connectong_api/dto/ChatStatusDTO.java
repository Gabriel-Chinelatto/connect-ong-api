package com.example.connectong_api.dto;

import java.time.LocalDateTime;

/**
 * Situacao do OUTRO participante do chat, do ponto de vista de quem consulta:
 * - online: teve atividade recente no chat (ultimo_visto ha poucos segundos);
 * - ultimoVisto: momento da ultima atividade (para "visto por ultimo as HH:MM");
 * - digitando: esta digitando agora (heartbeat recente, estado efemero em memoria).
 */
public class ChatStatusDTO {

    private boolean online;
    private LocalDateTime ultimoVisto;
    private boolean digitando;

    public ChatStatusDTO(boolean online, LocalDateTime ultimoVisto, boolean digitando) {
        this.online = online;
        this.ultimoVisto = ultimoVisto;
        this.digitando = digitando;
    }

    public boolean isOnline() { return online; }
    public LocalDateTime getUltimoVisto() { return ultimoVisto; }
    public boolean isDigitando() { return digitando; }
}
