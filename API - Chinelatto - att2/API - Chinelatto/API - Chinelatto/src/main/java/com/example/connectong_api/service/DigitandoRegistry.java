package com.example.connectong_api.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado EFEMERO de "digitando..." por match e lado (DOADOR/ONG), guardado em
 * memoria (nao vai para o banco: e transitorio e some sozinho). Cada heartbeat
 * do cliente que esta digitando atualiza o timestamp; o outro lado ve "digitando"
 * se o timestamp for recente (dentro da janela). Como o app usa polling, isto e
 * suficiente e sem custo de banco.
 */
@Component
public class DigitandoRegistry {

    // chave = interesseId + ":" + lado ; valor = instante do ultimo heartbeat.
    private final Map<String, Instant> ultimos = new ConcurrentHashMap<>();

    private String chave(Long interesseId, String lado) {
        return interesseId + ":" + lado;
    }

    /** Registra que o lado informado esta digitando no match agora. */
    public void marcar(Long interesseId, String lado) {
        ultimos.put(chave(interesseId, lado), Instant.now());
    }

    /** true se o lado informado digitou dentro da janela (ex.: ultimos 5s). */
    public boolean estaDigitando(Long interesseId, String lado, Duration janela) {
        Instant t = ultimos.get(chave(interesseId, lado));
        return t != null && t.isAfter(Instant.now().minus(janela));
    }
}
