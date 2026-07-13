package com.example.connectong_api.service;

import com.example.connectong_api.dto.RedacaoRequestDTO;
import com.example.connectong_api.dto.RedacaoResponseDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Ajuda a ONG a REDIGIR uma necessidade clara e convincente para doadores (usado
 * no painel da ONG). Duas camadas, como o AssistenteService:
 *
 *  1) IA (Groq): reescreve o rascunho da ONG num titulo (ate 60 chars) e uma
 *     descricao calorosa de 2 a 4 frases, SEM inventar numeros/contatos.
 *  2) FALLBACK por REGRAS (sem chave): capitaliza/limpa o rascunho, deriva um
 *     titulo e monta uma descricao a partir de um template com a categoria.
 * Sempre retorna algo (modo "ia" ou "regras").
 */
@Service
public class RedacaoService {

    @Autowired private ProvedorIA provedorIA;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_TITULO = 60;

    public RedacaoResponseDTO redigir(RedacaoRequestDTO req) {
        String titulo = req.getTitulo() == null ? "" : req.getTitulo().trim();
        String rascunho = req.getRascunho() == null ? "" : req.getRascunho().trim();
        String categoria = req.getCategoria() == null ? "" : req.getCategoria().trim();

        // Base textual para IA/regras: rascunho tem prioridade; senao o titulo.
        String base = !rascunho.isBlank() ? rascunho : titulo;

        if (provedorIA.disponivel() && !base.isBlank()) {
            RedacaoResponseDTO viaIa = tentarIa(titulo, rascunho, categoria);
            if (viaIa != null) return viaIa;
        }
        return porRegras(titulo, rascunho, categoria);
    }

    // ================================================================
    // CAMINHO DA IA
    // ================================================================
    private RedacaoResponseDTO tentarIa(String titulo, String rascunho, String categoria) {
        String sistema = "Voce ajuda uma ONG a escrever uma NECESSIDADE clara e "
                + "convincente para doadores. Reescreva o rascunho da ONG num titulo curto "
                + "e numa descricao calorosa, especifica e objetiva. NAO invente numeros, "
                + "datas, contatos, valores nem nada que nao esteja no rascunho. Escreva em "
                + "portugues do Brasil. Responda EXCLUSIVAMENTE com um JSON valido, sem texto "
                + "fora dele, no formato: {\"titulo\":\"ate 60 caracteres\",\"descricao\":\"2 a "
                + "4 frases\"}.";

        StringBuilder u = new StringBuilder();
        if (!categoria.isBlank()) u.append("Categoria: ").append(categoria).append("\n");
        if (!titulo.isBlank()) u.append("Titulo atual: ").append(titulo).append("\n");
        u.append("Rascunho da ONG: ").append(!rascunho.isBlank() ? rascunho : titulo);

        List<ProvedorIA.MensagemIA> mensagens = List.of(
                new ProvedorIA.MensagemIA("system", sistema),
                new ProvedorIA.MensagemIA("user", u.toString()));

        // ESCRITA natural, mas ancorada no rascunho: temperatura media e teto de
        // tokens suficiente para titulo + 2-4 frases (JSON curto).
        Optional<String> saida = provedorIA.completar(mensagens,
                ProvedorIA.OpcoesIA.de(0.55, 300));
        if (saida.isEmpty()) return null;
        return parsearJson(saida.get());
    }

    private RedacaoResponseDTO parsearJson(String resposta) {
        try {
            int ini = resposta.indexOf('{');
            int fim = resposta.lastIndexOf('}');
            if (ini < 0 || fim <= ini) return null;

            JsonNode raiz = objectMapper.readTree(resposta.substring(ini, fim + 1));
            String titulo = raiz.path("titulo").asText("").trim();
            String descricao = raiz.path("descricao").asText("").trim();
            if (titulo.isBlank() && descricao.isBlank()) return null;

            if (titulo.length() > MAX_TITULO) titulo = titulo.substring(0, MAX_TITULO).trim();
            return new RedacaoResponseDTO(titulo, descricao, "ia");
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================
    // FALLBACK POR REGRAS
    // ================================================================
    private RedacaoResponseDTO porRegras(String titulo, String rascunho, String categoria) {
        String base = !rascunho.isBlank() ? rascunho : titulo;
        base = limpar(base);

        // Titulo: usa o informado; senao deriva do rascunho (ate 60 chars).
        String tituloFinal = !titulo.isBlank() ? capitalizar(limpar(titulo)) : tituloDe(base);
        if (tituloFinal.isBlank()) {
            tituloFinal = categoria.isBlank() ? "Doação necessária" : "Doação de " + categoria.toLowerCase();
        }
        if (tituloFinal.length() > MAX_TITULO) {
            tituloFinal = tituloFinal.substring(0, MAX_TITULO).trim();
        }

        // Descricao: template caloroso + o texto do rascunho (sem inventar dados).
        StringBuilder d = new StringBuilder();
        String corpo = base.isBlank() ? tituloFinal : capitalizar(base);
        d.append(corpo);
        if (!corpo.endsWith(".") && !corpo.endsWith("!") && !corpo.endsWith("?")) d.append(".");
        if (!categoria.isBlank()) {
            d.append(" Toda contribuição de ").append(categoria.toLowerCase())
             .append(" faz diferença para quem atendemos.");
        } else {
            d.append(" Toda ajuda faz diferença para quem atendemos.");
        }
        d.append(" Se puder contribuir, entre em contato pela plataforma — muito obrigado!");

        return new RedacaoResponseDTO(tituloFinal, d.toString(), "regras");
    }

    // Deriva um titulo curto do texto: primeiras palavras, capitalizado, ate 60 chars.
    private String tituloDe(String texto) {
        if (texto == null || texto.isBlank()) return "";
        String limpo = limpar(texto);
        String[] palavras = limpo.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : palavras) {
            if (sb.length() + p.length() + 1 > MAX_TITULO) break;
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
        }
        return capitalizar(sb.toString().trim());
    }

    // Colapsa espacos/quebras e apara.
    private String limpar(String t) {
        if (t == null) return "";
        return t.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
    }

    // Primeira letra maiuscula (nao mexe no resto para preservar siglas).
    private String capitalizar(String t) {
        if (t == null || t.isBlank()) return "";
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }
}
