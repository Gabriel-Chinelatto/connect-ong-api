package com.example.connectong_api.util;

import java.text.Normalizer;
import java.util.List;

/**
 * Lista canonica de categorias do Connect ONG e normalizacao de variantes.
 *
 * Os valores canonicos sao armazenados SEM acento e no plural
 * ("Educacao", "Saude") para evitar problemas de charset no MySQL.
 * Variantes conhecidas (com acento, singular, caixa diferente) sao
 * mapeadas para o valor canonico; valores desconhecidos sao preservados
 * (apenas com trim), pois clientes antigos podem enviar categorias livres.
 */
public final class Categorias {

    /** Lista canonica (imutavel), na ordem exibida nos apps. */
    public static final List<String> CANONICAS = List.of(
            "Alimentos",
            "Roupas",
            "Higiene",
            "Brinquedos",
            "Educacao",
            "Saude"
    );

    private Categorias() {
        // classe utilitaria: nao instanciar
    }

    /**
     * Normaliza uma categoria para o valor canonico, quando reconhecida.
     * Comparacao case-insensitive, ignorando acentos e aceitando o singular
     * (ex.: "Alimento" -> "Alimentos", "Educação" -> "Educacao",
     * "SAÚDE" -> "Saude", "roupa" -> "Roupas").
     * Valores nulos/vazios sao devolvidos como estao; valores desconhecidos
     * sao devolvidos com trim (nao rejeitamos categorias legadas).
     */
    public static String normalizar(String categoria) {
        if (categoria == null) return null;

        String aparada = categoria.trim();
        if (aparada.isEmpty()) return aparada;

        String chave = semAcento(aparada).toLowerCase();

        for (String canonica : CANONICAS) {
            String base = canonica.toLowerCase();
            // aceita o proprio valor canonico e o singular (sem o "s" final)
            if (chave.equals(base) || (base.endsWith("s")
                    && chave.equals(base.substring(0, base.length() - 1)))) {
                return canonica;
            }
        }

        // desconhecida: preserva o valor original (apenas com trim)
        return aparada;
    }

    /** Compara duas categorias apos normalizacao (case-insensitive, sem acento). */
    public static boolean iguais(String a, String b) {
        if (a == null || b == null) return false;
        return semAcento(normalizar(a)).equalsIgnoreCase(semAcento(normalizar(b)));
    }

    /** Remove acentos/diacriticos (ex.: "Educação" -> "Educacao"). */
    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
