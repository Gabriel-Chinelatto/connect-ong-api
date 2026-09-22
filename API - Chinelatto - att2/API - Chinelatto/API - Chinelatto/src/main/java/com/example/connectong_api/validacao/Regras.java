package com.example.connectong_api.validacao;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras de FORMATO dos campos (item F-01 do Plano de Acao — o avaliador da
 * FECITEC: "nao tinha validacoes nos campos, podia colocar o que quisesse em
 * tudo"). Antes a API so limitava o TAMANHO: telefone aceitava "abc", CNPJ nao
 * conferia digito verificador, nome aceitava "!!!".
 *
 * <p>Funcoes puras, usadas pelas anotacoes deste pacote. As MESMAS regras estao
 * replicadas nos tres frontends (app do doador, painel da ONG e site) para o
 * erro aparecer embaixo do campo antes de enviar; a API continua sendo a
 * palavra final, porque qualquer um pode chamar a API sem passar pela tela.
 *
 * <p>Campo vazio/nulo e sempre VALIDO aqui: obrigatoriedade e papel do
 * {@code @NotBlank}. Assim a mesma regra serve para campo opcional.
 */
public final class Regras {

    private Regras() {}

    // DDDs em uso no Brasil (Anatel).
    private static final Set<Integer> DDDS = Set.of(
            11, 12, 13, 14, 15, 16, 17, 18, 19, 21, 22, 24, 27, 28,
            31, 32, 33, 34, 35, 37, 38, 41, 42, 43, 44, 45, 46, 47, 48, 49,
            51, 53, 54, 55, 61, 62, 63, 64, 65, 66, 67, 68, 69,
            71, 73, 74, 75, 77, 79, 81, 82, 83, 84, 85, 86, 87, 88, 89,
            91, 92, 93, 94, 95, 96, 97, 98, 99);

    private static final Set<String> UFS = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA",
            "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO");

    private static final Set<String> ESTADOS = Set.of(
            "acre", "alagoas", "amapa", "amazonas", "bahia", "ceara", "distrito federal",
            "espirito santo", "goias", "maranhao", "mato grosso", "mato grosso do sul",
            "minas gerais", "para", "paraiba", "parana", "pernambuco", "piaui", "rio de janeiro",
            "rio grande do norte", "rio grande do sul", "rondonia", "roraima", "santa catarina",
            "sao paulo", "sergipe", "tocantins");

    // Senhas que aparecem no topo de todo vazamento: 8+ caracteres com letra e
    // numero, e mesmo assim adivinhaveis.
    private static final Set<String> SENHAS_COMUNS = Set.of(
            "senha123", "senha1234", "password1", "abc12345", "abcd1234", "qwerty123",
            "12345678a", "a12345678", "admin123", "mudar123", "brasil123", "teste123");

    private static final Pattern SO_CARACTERES_DE_TELEFONE = Pattern.compile("[0-9()+\\-.\\s]+");
    private static final Pattern SO_CARACTERES_DE_CNPJ = Pattern.compile("[0-9A-Za-z./\\-\\s]+");
    // Nome de pessoa ou de lugar: letras (com acento), espaco, hifen, apostrofo e ponto.
    private static final Pattern NOME_PROPRIO = Pattern.compile("[\\p{L}\\s'’.\\-]+");
    private static final Pattern LETRA = Pattern.compile("\\p{L}");
    private static final Pattern CONTROLE = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");

    public static boolean vazio(String s) {
        return s == null || s.isBlank();
    }

    /** Telefone brasileiro com DDD: fixo (10 digitos) ou celular (11, comecando com 9). Aceita mascara e +55. */
    public static boolean telefone(String s) {
        if (vazio(s)) return true;
        if (!SO_CARACTERES_DE_TELEFONE.matcher(s).matches()) return false;
        String d = s.replaceAll("\\D", "");
        if ((d.length() == 12 || d.length() == 13) && d.startsWith("55")) d = d.substring(2);
        if (d.length() != 10 && d.length() != 11) return false;
        if (!DDDS.contains(Integer.parseInt(d.substring(0, 2)))) return false;
        String numero = d.substring(2);
        if (d.length() == 11 && numero.charAt(0) != '9') return false;
        if (d.length() == 10 && (numero.charAt(0) < '2' || numero.charAt(0) > '5')) return false;
        return !numero.chars().allMatch(c -> c == numero.charAt(0));
    }

    /**
     * CNPJ com digitos verificadores. Aceita o formato numerico e o ALFANUMERICO
     * que a Receita adotou a partir de julho de 2026 (12 primeiros caracteres
     * podem ser letras; os 2 digitos verificadores continuam numericos; o valor
     * de cada caractere e o codigo ASCII menos 48).
     */
    public static boolean cnpj(String s) {
        if (vazio(s)) return true;
        if (!SO_CARACTERES_DE_CNPJ.matcher(s).matches()) return false;
        String c = s.replaceAll("[./\\-\\s]", "").toUpperCase(Locale.ROOT);
        if (c.length() != 14) return false;
        if (!c.substring(0, 12).matches("[0-9A-Z]{12}") || !c.substring(12).matches("\\d{2}")) return false;
        if (c.chars().allMatch(ch -> ch == c.charAt(0))) return false;
        int[] pesos1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] pesos2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int dv1 = digitoCnpj(c, pesos1);
        int dv2 = digitoCnpj(c.substring(0, 12) + dv1, pesos2);
        return c.charAt(12) - '0' == dv1 && c.charAt(13) - '0' == dv2;
    }

    private static int digitoCnpj(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) soma += (base.charAt(i) - '0') * pesos[i];
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    /**
     * Texto livre "de verdade" (titulo, nome de ONG, descricao): precisa ter ao
     * menos 2 letras (recusa "!!!!", "12345", "...") e nao pode ter marcacao
     * HTML nem caracteres de controle.
     */
    public static boolean textoLegivel(String s) {
        if (vazio(s)) return true;
        if (s.indexOf('<') >= 0 || s.indexOf('>') >= 0) return false;
        if (CONTROLE.matcher(s).find()) return false;
        return LETRA.matcher(s).results().limit(2).count() == 2;
    }

    /** Nome de pessoa ou de cidade: so letras, espacos, hifen, apostrofo e ponto; ao menos 2 letras. */
    public static boolean nomeProprio(String s) {
        if (vazio(s)) return true;
        String t = s.trim();
        return NOME_PROPRIO.matcher(t).matches() && LETRA.matcher(t).results().limit(2).count() == 2;
    }

    /** Sigla (SP) ou nome do estado (Sao Paulo / São Paulo), sem diferenciar maiusculas. */
    public static boolean uf(String s) {
        if (vazio(s)) return true;
        String t = s.trim();
        if (t.length() == 2) return UFS.contains(t.toUpperCase(Locale.ROOT));
        return ESTADOS.contains(semAcento(t).toLowerCase(Locale.ROOT).replaceAll("\\s+", " "));
    }

    /** Senha nova: 8 a 100 caracteres, com letra e numero, fora da lista das mais comuns. */
    public static boolean senhaForte(String s) {
        if (s == null) return true;
        if (s.length() < 8 || s.length() > 100) return false;
        if (!LETRA.matcher(s).find() || !s.matches(".*\\d.*")) return false;
        return !SENHAS_COMUNS.contains(s.toLowerCase(Locale.ROOT));
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
