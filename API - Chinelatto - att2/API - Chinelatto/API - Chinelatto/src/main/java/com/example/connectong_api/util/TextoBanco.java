package com.example.connectong_api.util;

/**
 * Saneamento de texto ANTES de gravar no banco.
 *
 * Motivo real (02/09/2026): o MySQL da escola usa "utf8" de 3 BYTES, que NAO
 * aceita caractere fora do BMP (emoji, bandeiras, simbolos raros). Ao salvar o
 * "Sobre" da ONG com um emoji, o insert estourava
 *   SQL Error 1366 - Incorrect string value: '\xF0\x9F\x92\x9A'
 * e a API respondia 500 "Ocorreu um erro inesperado no servidor." — a ONG nao
 * conseguia salvar o perfil e nao havia pista do porque.
 *
 * Em vez de derrubar o salvamento, tiramos so os caracteres impossiveis e
 * gravamos o resto do texto.
 */
public final class TextoBanco {

    private TextoBanco() {}

    /**
     * Devolve o texto sem os caracteres que o utf8 de 3 bytes nao guarda
     * (tudo acima de U+FFFF). Null continua null; texto ja compativel volta
     * como veio (mesma instancia).
     */
    public static String semEmoji(String texto) {
        if (texto == null || texto.isEmpty()) return texto;

        StringBuilder limpo = null;
        int i = 0;
        while (i < texto.length()) {
            int cp = texto.codePointAt(i);
            int passo = Character.charCount(cp);
            if (cp > 0xFFFF) {
                // Achou o primeiro caractere impossivel: comeca a copia a
                // partir daqui (ate agora o texto era igual ao original).
                if (limpo == null) limpo = new StringBuilder(texto.length()).append(texto, 0, i);
            } else if (limpo != null) {
                limpo.appendCodePoint(cp);
            }
            i += passo;
        }

        if (limpo == null) return texto;                 // nada a remover
        // Remover um emoji costuma deixar espaco duplo/sobra nas pontas.
        return limpo.toString().replaceAll("[ \t]{2,}", " ").trim();
    }
}
