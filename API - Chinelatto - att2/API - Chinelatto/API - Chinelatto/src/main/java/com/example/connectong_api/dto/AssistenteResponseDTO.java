package com.example.connectong_api.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Resposta de POST /assistente.
 *
 * {
 *   "resposta": "texto acolhedor em PT-BR",
 *   "sugestoes": [ {"tipo":"ONG|NECESSIDADE","id":123,"titulo":"...","subtitulo":"..."} ],
 *   "titulo": "Doar roupas",
 *   "modo": "ia" | "regras"
 * }
 *
 * - sugestoes: ONGs/necessidades REAIS recomendadas (o app mostra como cards
 *   clicaveis que abrem o perfil da ONG ou a necessidade). Vem VAZIO em conversa
 *   geral/off-topic — os cards so aparecem quando o doador pede recomendacao de
 *   doacao (onde/o que doar, achar ONGs, foto de item doavel).
 * - titulo: resumo curto (2-4 palavras, PT-BR, sem ids/emoji) do assunto da
 *   conversa, para o app nomear o chat. A IA gera; no fallback e derivado da 1a
 *   mensagem. Campo opcional (retrocompatibilidade): clientes antigos o ignoram.
 * - modo: "ia" quando a resposta veio do provedor de IA (Groq); "regras" quando
 *   veio do fallback local (sem chave configurada, ou a IA falhou/timeout/429).
 */
public class AssistenteResponseDTO {

    private String resposta;
    private List<Sugestao> sugestoes = new ArrayList<>();
    private String titulo;
    private String modo;

    public AssistenteResponseDTO() {}

    public AssistenteResponseDTO(String resposta, List<Sugestao> sugestoes, String modo) {
        this.resposta = resposta;
        this.sugestoes = sugestoes != null ? sugestoes : new ArrayList<>();
        this.modo = modo;
    }

    public AssistenteResponseDTO(String resposta, List<Sugestao> sugestoes,
                                 String titulo, String modo) {
        this(resposta, sugestoes, modo);
        this.titulo = titulo;
    }

    public String getResposta() { return resposta; }
    public void setResposta(String resposta) { this.resposta = resposta; }

    public List<Sugestao> getSugestoes() { return sugestoes; }
    public void setSugestoes(List<Sugestao> sugestoes) { this.sugestoes = sugestoes; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getModo() { return modo; }
    public void setModo(String modo) { this.modo = modo; }

    /** Card clicavel: aponta para uma ONG ou uma necessidade real. */
    public static class Sugestao {
        private String tipo; // "ONG" ou "NECESSIDADE"
        private Long id;
        private String titulo;
        private String subtitulo;

        public Sugestao() {}

        public Sugestao(String tipo, Long id, String titulo, String subtitulo) {
            this.tipo = tipo;
            this.id = id;
            this.titulo = titulo;
            this.subtitulo = subtitulo;
        }

        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getTitulo() { return titulo; }
        public void setTitulo(String titulo) { this.titulo = titulo; }

        public String getSubtitulo() { return subtitulo; }
        public void setSubtitulo(String subtitulo) { this.subtitulo = subtitulo; }
    }
}
