package com.example.connectong_api.service;

import java.util.List;
import java.util.Optional;

/**
 * Abstracao de um provedor de IA conversacional (chat completion).
 *
 * Mantemos a interface para NAO acoplar o AssistenteService a Groq: trocar de
 * provedor (OpenAI, Together, Ollama local, etc.) e so criar outra implementacao
 * desta interface. Hoje a unica implementacao e o {@link GroqService} (API
 * gratuita compativel com OpenAI).
 *
 * Contrato: {@link #completar} recebe a conversa JA montada (system + historico +
 * mensagem do usuario) e devolve o texto do assistente, ou {@link Optional#empty()}
 * quando o provedor esta indisponivel (sem chave), falha, da timeout ou retorna
 * erro (ex.: 429 rate limit). O chamador entao usa o FALLBACK por regras — o
 * assistente nunca deixa o usuario sem resposta.
 */
public interface ProvedorIA {

    /** true quando o provedor esta configurado e pode ser chamado (ex.: chave presente). */
    boolean disponivel();

    /**
     * Envia a conversa e retorna o texto de resposta do assistente.
     * Nunca lanca: em qualquer erro/timeout devolve {@link Optional#empty()}.
     */
    Optional<String> completar(List<MensagemIA> mensagens);

    /**
     * true quando o provedor de VISAO (multimodal) esta configurado. Hoje usa a
     * mesma chave do texto; separado para o chamador poder decidir o roteamento.
     */
    default boolean visaoDisponivel() { return disponivel(); }

    /**
     * VISAO (multimodal): envia a conversa + UMA imagem (data URL ou base64 puro)
     * e retorna o texto do assistente. A imagem e anexada a ultima mensagem do
     * usuario no formato OpenAI-compat (content array com type=image_url).
     * Nunca lanca: em qualquer erro/timeout devolve {@link Optional#empty()} — o
     * chamador entao mostra um fallback amigavel. A imagem NUNCA e persistida nem
     * logada.
     */
    Optional<String> completarComImagem(List<MensagemIA> mensagens, String imagemBase64);

    /**
     * Uma mensagem no formato do chat: papel = "system" | "user" | "assistant".
     * (record = imutavel; sem dependencia nova.)
     */
    record MensagemIA(String papel, String conteudo) {}
}
