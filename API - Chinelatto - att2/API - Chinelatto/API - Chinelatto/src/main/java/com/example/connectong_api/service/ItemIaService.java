package com.example.connectong_api.service;

import com.example.connectong_api.util.Categorias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Estima o PESO (kg) e a CATEGORIA de um item descrito em texto livre (PT-BR),
 * para alimentar o calculo de frete. Duas camadas, como o AssistenteService:
 *
 *  1) IA (Groq, gratuita): extrai {pesoKg, categoria, resumo} de uma descricao,
 *     estimando o peso TOTAL considerando quantidade (ex.: "10 sacos de arroz de
 *     1kg" -> 10 kg). Parse robusto (do 1o '{' ao ultimo '}').
 *  2) FALLBACK por REGRAS (sem chave / IA falhou / peso invalido): tabela de
 *     pesos medios por palavra-chave + deteccao de categoria pelas mesmas
 *     palavras. SEMPRE devolve algo (piso de 1 kg). modo "regras".
 */
@Service
public class ItemIaService {

    @Autowired private ProvedorIA provedorIA;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Resultado da estimativa: peso total (kg), categoria canonica e um resumo. */
    public record ItemInfo(double pesoKg, String categoria, String resumo, String modo) {}

    // Piso de peso: nenhum frete e calculado com menos de 1 kg.
    private static final double PISO_KG = 1.0;

    // Peso medio (kg) por UNIDADE, por palavra-chave (sem acento, minusculo).
    // Ordem importa: a 1a chave que casar o texto define o peso unitario base.
    private static final Map<String, Double> PESO_POR_PALAVRA = new LinkedHashMap<>();
    static {
        // Alimentos
        PESO_POR_PALAVRA.put("cesta basica", 12.0);
        PESO_POR_PALAVRA.put("cesta", 12.0);
        PESO_POR_PALAVRA.put("arroz", 5.0);
        PESO_POR_PALAVRA.put("feijao", 1.0);
        PESO_POR_PALAVRA.put("acucar", 1.0);
        PESO_POR_PALAVRA.put("leite", 1.0);
        PESO_POR_PALAVRA.put("oleo", 0.9);
        PESO_POR_PALAVRA.put("macarrao", 0.5);
        PESO_POR_PALAVRA.put("farinha", 1.0);
        PESO_POR_PALAVRA.put("cafe", 0.5);
        PESO_POR_PALAVRA.put("marmita", 0.6);
        PESO_POR_PALAVRA.put("racao", 15.0);
        // Roupas
        PESO_POR_PALAVRA.put("cobertor", 1.5);
        PESO_POR_PALAVRA.put("manta", 1.2);
        PESO_POR_PALAVRA.put("casaco", 0.7);
        PESO_POR_PALAVRA.put("agasalho", 0.6);
        PESO_POR_PALAVRA.put("moletom", 0.6);
        PESO_POR_PALAVRA.put("calca", 0.5);
        PESO_POR_PALAVRA.put("blusa", 0.3);
        PESO_POR_PALAVRA.put("camiseta", 0.2);
        PESO_POR_PALAVRA.put("sapato", 0.8);
        PESO_POR_PALAVRA.put("calcado", 0.8);
        PESO_POR_PALAVRA.put("tenis", 0.8);
        PESO_POR_PALAVRA.put("roupa", 0.4);
        // Higiene
        PESO_POR_PALAVRA.put("fralda", 1.5);
        PESO_POR_PALAVRA.put("sabonete", 0.1);
        PESO_POR_PALAVRA.put("shampoo", 0.4);
        PESO_POR_PALAVRA.put("sabao", 1.0);
        PESO_POR_PALAVRA.put("absorvente", 0.3);
        PESO_POR_PALAVRA.put("pasta de dente", 0.1);
        PESO_POR_PALAVRA.put("escova", 0.05);
        PESO_POR_PALAVRA.put("papel higienico", 1.2);
        // Brinquedos
        PESO_POR_PALAVRA.put("brinquedo", 0.5);
        PESO_POR_PALAVRA.put("boneca", 0.4);
        PESO_POR_PALAVRA.put("bola", 0.4);
        PESO_POR_PALAVRA.put("pelucia", 0.3);
        PESO_POR_PALAVRA.put("jogo", 0.6);
        PESO_POR_PALAVRA.put("quebra-cabeca", 0.5);
        // Educacao
        PESO_POR_PALAVRA.put("livro", 0.5);
        PESO_POR_PALAVRA.put("caderno", 0.4);
        PESO_POR_PALAVRA.put("mochila", 0.6);
        PESO_POR_PALAVRA.put("apostila", 0.4);
        PESO_POR_PALAVRA.put("material escolar", 1.0);
        PESO_POR_PALAVRA.put("lapis", 0.02);
        PESO_POR_PALAVRA.put("caneta", 0.02);
        // Saude
        PESO_POR_PALAVRA.put("remedio", 0.1);
        PESO_POR_PALAVRA.put("medicament", 0.1);
        PESO_POR_PALAVRA.put("mascara", 0.02);
        PESO_POR_PALAVRA.put("curativo", 0.05);
    }

    // Palavra-chave -> categoria canonica (mesma logica do AssistenteService).
    private static final Map<String, List<String>> PALAVRAS_CATEGORIA = new LinkedHashMap<>();
    static {
        PALAVRAS_CATEGORIA.put("Alimentos", List.of(
                "aliment", "comida", "cesta", "leite", "arroz", "feijao", "acucar",
                "oleo", "macarrao", "farinha", "cafe", "mantimento", "racao",
                "nao pereciv", "marmita"));
        PALAVRAS_CATEGORIA.put("Roupas", List.of(
                "roupa", "agasalho", "casaco", "cobertor", "blusa", "calca",
                "sapato", "calcado", "tenis", "moletom", "manta", "camiseta"));
        PALAVRAS_CATEGORIA.put("Higiene", List.of(
                "higiene", "fralda", "sabonete", "shampoo", "absorvente",
                "papel higienico", "pasta de dente", "escova", "sabao"));
        PALAVRAS_CATEGORIA.put("Brinquedos", List.of(
                "brinquedo", "jogo", "boneca", "bola", "pelucia", "quebra-cabeca"));
        PALAVRAS_CATEGORIA.put("Educacao", List.of(
                "educ", "escolar", "caderno", "livro", "mochila", "lapis",
                "caneta", "material escolar", "apostila"));
        PALAVRAS_CATEGORIA.put("Saude", List.of(
                "saude", "remedio", "medicament", "veterinari", "mascara",
                "curativo", "fisioterap"));
    }

    /**
     * Estima peso total (kg), categoria e resumo de um item. Nunca lanca; sempre
     * devolve um ItemInfo (piso de 1 kg). Usa a IA quando ha chave; senao regras.
     *
     * Sobrecarga sem dica de categoria: a IA/regra deduzem a categoria pelo texto.
     */
    public ItemInfo estimar(String texto, Integer quantidade) {
        return estimar(texto, quantidade, null);
    }

    /**
     * Igual ao {@link #estimar(String, Integer)}, mas HONRANDO a categoria que o
     * usuario escolheu (categoriaHint). Quando ela vem preenchida:
     *   - vira a categoria FINAL da resposta (a IA/regra NAO a sobrescreve);
     *   - e informada ao prompt da IA e a tabela de pesos do fallback para melhorar
     *     a estimativa de PESO (ex.: "morango" com categoria Alimentos nao vira Higiene).
     * Quando categoriaHint e null/vazia, comportamento identico a sobrecarga antiga.
     */
    public ItemInfo estimar(String texto, Integer quantidade, String categoriaHint) {
        String desc = texto == null ? "" : texto.trim();
        // Categoria escolhida pelo usuario (canonizada). null => IA/regra deduzem.
        String catHint = (categoriaHint != null && !categoriaHint.isBlank())
                ? Categorias.normalizar(categoriaHint.trim()) : null;

        if (provedorIA.disponivel() && !desc.isBlank()) {
            ItemInfo viaIa = tentarIa(desc, quantidade, catHint);
            if (viaIa != null) return viaIa;
        }
        return porRegras(desc, quantidade, catHint);
    }

    // ================================================================
    // CAMINHO DA IA
    // ================================================================
    private ItemInfo tentarIa(String texto, Integer quantidade, String catHint) {
        String sistema = "Voce estima peso e categoria de itens de DOACAO a partir de "
                + "uma descricao em portugues do Brasil. Considere a quantidade e o item "
                + "para estimar o peso TOTAL em quilogramas (ex.: '10 sacos de arroz de 1kg' "
                + "-> pesoKg 10). Categorias validas: Alimentos, Roupas, Higiene, Brinquedos, "
                + "Educacao, Saude, Outros. Responda EXCLUSIVAMENTE com um JSON valido, sem "
                + "texto fora dele, no formato: {\"pesoKg\": numero, \"categoria\": \"...\", "
                + "\"resumo\": \"texto curto\"}. Nao invente dados alem do que a descricao diz.";

        StringBuilder u = new StringBuilder();
        u.append("Descricao do item: ").append(texto);
        if (quantidade != null && quantidade > 0) {
            u.append("\nQuantidade informada separadamente: ").append(quantidade);
        }
        if (catHint != null) {
            // A categoria ja foi ESCOLHIDA pelo usuario: informe-a para calibrar o
            // peso, mas a IA nao deve muda-la (o parse forca essa categoria).
            u.append("\nCategoria JA definida pelo usuario (use-a como referencia para "
                    + "estimar o peso; nao mude a categoria): ").append(catHint);
        }

        List<ProvedorIA.MensagemIA> mensagens = List.of(
                new ProvedorIA.MensagemIA("system", sistema),
                new ProvedorIA.MensagemIA("user", u.toString()));

        Optional<String> saida = provedorIA.completar(mensagens);
        if (saida.isEmpty()) return null;
        return parsearJson(saida.get(), texto, quantidade, catHint);
    }

    private ItemInfo parsearJson(String resposta, String texto, Integer quantidade, String catHint) {
        try {
            int ini = resposta.indexOf('{');
            int fim = resposta.lastIndexOf('}');
            if (ini < 0 || fim <= ini) return null;

            JsonNode raiz = objectMapper.readTree(resposta.substring(ini, fim + 1));
            double pesoKg = raiz.path("pesoKg").asDouble(0);
            if (pesoKg <= 0) return null; // peso invalido -> fallback por regras

            // Categoria escolhida pelo usuario vence; senao a que a IA deduziu.
            String categoria = catHint != null
                    ? catHint
                    : normalizarCategoria(raiz.path("categoria").asText("").trim(), texto);
            String resumo = raiz.path("resumo").asText("").trim();
            if (resumo.isBlank()) resumo = resumoPadrao(texto, quantidade);

            double arredondado = Math.max(PISO_KG, Math.round(pesoKg * 100.0) / 100.0);
            return new ItemInfo(arredondado, categoria, resumo, "ia");
        } catch (Exception e) {
            return null;
        }
    }

    // ================================================================
    // FALLBACK POR REGRAS
    // ================================================================
    private ItemInfo porRegras(String texto, Integer quantidade, String catHint) {
        String norm = normalizar(texto);

        // Peso unitario base: 1a palavra-chave que casar. Sem match, usamos o peso
        // TIPICO da categoria escolhida (catHint) quando houver; senao 1 kg generico.
        double pesoUnitario = catHint != null ? pesoTipicoCategoria(catHint) : 1.0;
        for (Map.Entry<String, Double> e : PESO_POR_PALAVRA.entrySet()) {
            if (norm.contains(e.getKey())) {
                pesoUnitario = e.getValue();
                break;
            }
        }

        // Quantidade: usa a informada; senao tenta extrair um numero do texto; senao 1.
        int qtd = 1;
        if (quantidade != null && quantidade > 0) {
            qtd = quantidade;
        } else {
            Integer extraida = extrairQuantidade(norm);
            if (extraida != null) qtd = extraida;
        }

        double peso = Math.max(PISO_KG, pesoUnitario * qtd);
        peso = Math.round(peso * 100.0) / 100.0;

        // Categoria escolhida pelo usuario vence; senao detecta pelo texto.
        String categoria = catHint != null ? catHint : detectarCategoria(norm);
        String resumo = resumoPadrao(texto, quantidade);
        return new ItemInfo(peso, categoria, resumo, "regras");
    }

    // Peso unitario TIPICO (kg) por categoria canonica — usado no fallback quando o
    // texto nao casa nenhuma palavra-chave, mas a categoria foi escolhida. Calibra a
    // estimativa melhor do que o generico de 1 kg.
    private double pesoTipicoCategoria(String categoria) {
        if (categoria == null) return 1.0;
        switch (Categorias.normalizar(categoria)) {
            case "Alimentos":  return 1.0;
            case "Roupas":     return 0.5;
            case "Higiene":    return 0.4;
            case "Brinquedos": return 0.5;
            case "Educacao":   return 0.4;
            case "Saude":      return 0.2;
            default:           return 1.0;
        }
    }

    // Extrai o 1o numero inteiro do texto (ex.: "10 cobertores" -> 10). null se nao houver.
    private Integer extrairQuantidade(String norm) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{1,4})\\b").matcher(norm);
        if (m.find()) {
            try {
                int v = Integer.parseInt(m.group(1));
                if (v > 0) return v;
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    private String detectarCategoria(String norm) {
        for (Map.Entry<String, List<String>> e : PALAVRAS_CATEGORIA.entrySet()) {
            for (String chave : e.getValue()) {
                if (norm.contains(chave)) return e.getKey();
            }
        }
        return "Outros";
    }

    // Se a IA devolveu uma categoria reconhecida, canoniza; senao detecta pelo texto.
    private String normalizarCategoria(String categoriaIa, String texto) {
        if (categoriaIa != null && !categoriaIa.isBlank()) {
            String norm = normalizar(categoriaIa);
            if (norm.equals("outros")) return "Outros";
            for (String canonica : Categorias.CANONICAS) {
                if (Categorias.iguais(categoriaIa, canonica)) return canonica;
            }
        }
        return detectarCategoria(normalizar(texto));
    }

    private String resumoPadrao(String texto, Integer quantidade) {
        String t = texto == null ? "" : texto.trim();
        if (t.isBlank()) return "Item para doacao";
        if (quantidade != null && quantidade > 0) {
            return quantidade + "x " + t;
        }
        return t;
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().trim();
    }
}
