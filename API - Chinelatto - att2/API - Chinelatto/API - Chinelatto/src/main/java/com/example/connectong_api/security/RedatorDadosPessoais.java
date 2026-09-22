package com.example.connectong_api.security;

import java.util.regex.Pattern;

/**
 * Minimizacao de dados antes da IA externa (item F-10 do Plano de Acao; LGPD
 * art. 6, III — "necessidade"). O texto que a PESSOA digita (pergunta ao
 * assistente, rascunho para a IA melhorar) sai do nosso servidor para o
 * provedor de IA. Se alguem escrever "meu zap e (19) 99876-5432", esse numero
 * nao tem por que viajar: a IA nao precisa dele para responder.
 *
 * <p>Troca por marcadores legiveis ([e-mail], [telefone], [CPF], [CNPJ]) — a IA
 * ainda entende que ali havia um contato, so nao recebe o dado.
 */
public final class RedatorDadosPessoais {

    private RedatorDadosPessoais() {}

    private static final Pattern EMAIL =
            Pattern.compile("[\\p{L}0-9._%+-]+@[\\p{L}0-9.-]+\\.[\\p{L}]{2,}");
    // CNPJ antes do CPF e do telefone: tem mais digitos e contem trechos que casariam com eles.
    private static final Pattern CNPJ =
            Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");
    private static final Pattern CPF =
            Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    // (19) 99876-5432 | 19 3451-2000 | +55 19 998765432 | 19998765432
    private static final Pattern TELEFONE =
            Pattern.compile("(?<![\\d])(?:\\+?55\\s?)?(?:\\(?\\d{2}\\)?\\s?)(?:9\\s?\\d{4}|\\d{4})[-.\\s]?\\d{4}(?![\\d])");

    public static String redigir(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        String r = EMAIL.matcher(texto).replaceAll("[e-mail]");
        r = CNPJ.matcher(r).replaceAll("[CNPJ]");
        r = CPF.matcher(r).replaceAll("[CPF]");
        r = TELEFONE.matcher(r).replaceAll("[telefone]");
        return r;
    }
}
