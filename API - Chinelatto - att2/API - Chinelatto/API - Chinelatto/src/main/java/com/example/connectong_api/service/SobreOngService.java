package com.example.connectong_api.service;

import com.example.connectong_api.dto.SobreOngRequestDTO;
import com.example.connectong_api.dto.SobreOngResponseDTO;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.util.Categorias;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ajuda a ONG a escrever/refinar o texto "Sobre" (descricao institucional) do
 * perfil dela, com um LOOP de ajuste em linguagem natural. A ONG manda um rascunho
 * e, opcionalmente, um pedido de ajuste ("deixe mais curto", "mencione que
 * atendemos criancas"); a IA reformula o texto anterior aplicando o pedido —
 * quantas vezes quiser.
 *
 * A IA usa os DADOS REAIS da ONG (nome, cidade, verificacao, nota, numero de
 * necessidades abertas e as categorias que ela costuma pedir) para NAO inventar
 * informacao. Duas camadas, como os outros apoios de IA:
 *
 *  1) IA (Groq): reescreve o "Sobre" em 2 a 4 frases, caloroso e especifico, SEM
 *     inventar contato/CNPJ/numeros. Aceita JSON {"descricao":"..."} ou texto puro.
 *  2) FALLBACK por REGRAS (sem chave): compoe a descricao a partir do rascunho
 *     limpo + os dados reais; aplica ajustes minimos (ex.: encurtar).
 * Sempre retorna algo. ONG inexistente/excluida -> texto generico curto (modo "regras").
 */
@Service
public class SobreOngService {

    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private ProvedorIA provedorIA;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Dados reais coletados para uma ONG (grounding, para nao inventar). */
    private static class Dados {
        String nome;
        String cidade;
        boolean verificada;
        double notaMedia;
        int totalAvaliacoes;
        int necessidadesAbertas;
        List<String> categoriasPedidas = new ArrayList<>();
    }

    public SobreOngResponseDTO gerar(SobreOngRequestDTO req) {
        Long ongId = req.getOngId();
        Ong ong = ongId == null ? null : ongRepository.findById(ongId).orElse(null);
        if (ong == null || ong.getDataExclusao() != null) {
            // ONG inexistente/excluida: texto generico curto (nao quebra o app).
            return new SobreOngResponseDTO(
                    "Somos uma organização social que atua na comunidade e conta com o "
                    + "apoio de doadores para transformar vidas. Toda ajuda é bem-vinda.",
                    "regras");
        }

        Dados d = coletar(ong);
        String rascunho = req.getRascunho() == null ? "" : req.getRascunho().trim();
        String ajuste = req.getAjuste() == null ? "" : req.getAjuste().trim();

        if (provedorIA.disponivel()) {
            SobreOngResponseDTO viaIa = tentarIa(d, rascunho, ajuste);
            if (viaIa != null) return viaIa;
        }
        return new SobreOngResponseDTO(porRegras(d, rascunho, ajuste), "regras");
    }

    // ================================================================
    // COLETA DE DADOS REAIS
    // ================================================================
    private Dados coletar(Ong ong) {
        Dados d = new Dados();
        d.nome = ong.getNome();
        d.cidade = ong.getCidade();
        d.verificada = ong.getVerificada();
        d.notaMedia = ong.getNotaMedia();
        d.totalAvaliacoes = ong.getTotalAvaliacoes();

        int abertas = 0;
        Set<String> categorias = new LinkedHashSet<>();
        for (Necessidade nec : necessidadeRepository.findByOngId(ong.getId())) {
            boolean aberta = nec.getStatus() == null || nec.getStatus().isBlank()
                    || "ABERTA".equalsIgnoreCase(nec.getStatus());
            if (!aberta) continue;
            abertas++;
            if (temTexto(nec.getCategoria())) {
                categorias.add(Categorias.normalizar(nec.getCategoria()));
            }
        }
        d.necessidadesAbertas = abertas;
        d.categoriasPedidas = new ArrayList<>(categorias);
        return d;
    }

    // ================================================================
    // CAMINHO DA IA
    // ================================================================
    private SobreOngResponseDTO tentarIa(Dados d, String rascunho, String ajuste) {
        String sistema = "Voce ajuda uma ONG a escrever o texto 'Sobre' (institucional) do "
                + "perfil dela, em portugues do Brasil, 2 a 4 frases, caloroso e especifico, "
                + "SEM inventar dados — use apenas o rascunho e os dados reais fornecidos. "
                + "Nunca invente contato, CNPJ, valores nem numeros que nao estejam nos dados. "
                + "O texto e escrito na 1a pessoa do plural (ex.: 'Somos...', 'Atendemos...') ou "
                + "de forma neutra, como aparecera no perfil publico. "
                + "Responda EXCLUSIVAMENTE com um JSON valido no formato "
                + "{\"descricao\":\"o texto do Sobre\"} e NADA fora dele. NAO inclua titulo, "
                + "markdown, asteriscos, aspas ao redor, nem frases como 'Aqui esta' ou "
                + "'Segue uma sugestao' — apenas o JSON com o texto final, direto.";

        StringBuilder u = new StringBuilder();
        u.append("Dados reais da ONG (use como base, nao invente):\n");
        u.append("- Nome: ").append(d.nome).append("\n");
        if (temTexto(d.cidade)) u.append("- Cidade: ").append(d.cidade).append("\n");
        u.append("- Verificada: ").append(d.verificada ? "sim" : "nao").append("\n");
        if (d.totalAvaliacoes > 0) {
            u.append("- Nota media: ").append(d.notaMedia)
             .append(" (").append(d.totalAvaliacoes).append(" avaliacoes)\n");
        }
        u.append("- Necessidades abertas: ").append(d.necessidadesAbertas).append("\n");
        if (!d.categoriasPedidas.isEmpty()) {
            u.append("- Costuma pedir: ").append(String.join(", ", d.categoriasPedidas)).append("\n");
        }
        u.append("\nRascunho atual da ONG: ")
         .append(rascunho.isBlank() ? "(vazio — escreva um 'Sobre' a partir dos dados reais)" : rascunho);
        if (!ajuste.isBlank()) {
            u.append("\n\nO usuario pediu para AJUSTAR o texto anterior. Reformule o rascunho "
                    + "acima aplicando este pedido, mantendo os dados reais: ").append(ajuste);
        }

        List<ProvedorIA.MensagemIA> mensagens = List.of(
                new ProvedorIA.MensagemIA("system", sistema),
                new ProvedorIA.MensagemIA("user", u.toString()));

        Optional<String> saida = provedorIA.completar(mensagens);
        if (saida.isEmpty()) return null;

        String descricao = extrairDescricao(saida.get());
        if (descricao == null || descricao.isBlank()) return null;
        return new SobreOngResponseDTO(descricao, "ia");
    }

    // Aceita JSON {"descricao":"..."} OU texto puro; sempre LIMPA o resultado
    // (remove preambulo, markdown, titulo e aspas) e limita o tamanho.
    private String extrairDescricao(String texto) {
        String bruto = texto;
        try {
            int ini = texto.indexOf('{');
            int fim = texto.lastIndexOf('}');
            if (ini >= 0 && fim > ini) {
                JsonNode raiz = objectMapper.readTree(texto.substring(ini, fim + 1));
                String desc = raiz.path("descricao").asText("").trim();
                if (!desc.isBlank()) bruto = desc;
            }
        } catch (Exception ignored) {
            // nao era JSON: limpa o texto puro abaixo.
        }
        return limparDescricaoIa(bruto);
    }

    /**
     * Deixa o texto pronto para o perfil, mesmo quando a IA "enfeita" a resposta:
     * remove markdown (**, *, #, `), linhas de titulo ("Sobre a X"), preambulos
     * ("Aqui esta uma sugestao:", "Segue...", "Claro!"), aspas ao redor, colapsa
     * quebras/espacos e limita a ~600 chars cortando na ultima frase.
     */
    private String limparDescricaoIa(String texto) {
        if (texto == null) return "";
        String s = texto.trim();

        // Tira cercas de codigo e markdown inline.
        s = s.replaceAll("```+[a-zA-Z]*", " ")
             .replace("`", "")
             .replaceAll("[*_#]+", "");

        // Remove um preambulo do tipo "Aqui esta ... :" ou "Segue ... :" no inicio
        // (ate o primeiro ':' seguido de espaco/quebra), se houver.
        String semAcento = normalizar(s);
        if (semAcento.startsWith("aqui esta") || semAcento.startsWith("segue")
                || semAcento.startsWith("claro") || semAcento.startsWith("perfeito")
                || semAcento.startsWith("com certeza")) {
            int dp = s.indexOf(':');
            if (dp >= 0 && dp < 120) s = s.substring(dp + 1).trim();
        }

        // Processa linha a linha: descarta linhas de titulo curtas ("Sobre a X").
        String[] linhas = s.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        for (String ln : linhas) {
            String l = ln.trim();
            if (l.isEmpty()) continue;
            String ln2 = normalizar(l);
            boolean titulo = (ln2.startsWith("sobre") && l.length() <= 40 && !l.endsWith("."));
            if (titulo) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(l);
        }
        s = sb.length() > 0 ? sb.toString() : s;

        // Colapsa espacos e remove aspas ao redor (retas ou tipograficas).
        s = s.replaceAll("\\s{2,}", " ").trim();
        s = s.replaceAll("^[\"'\\u201C\\u201D\\u2018\\u2019]+", "")
             .replaceAll("[\"'\\u201C\\u201D\\u2018\\u2019]+$", "")
             .trim();

        // Limita o tamanho, cortando na ultima frase completa antes de 600 chars.
        final int max = 600;
        if (s.length() > max) {
            String corte = s.substring(0, max);
            int ult = Math.max(corte.lastIndexOf('.'),
                      Math.max(corte.lastIndexOf('!'), corte.lastIndexOf('?')));
            s = (ult > 100 ? corte.substring(0, ult + 1) : corte).trim();
        }
        return s;
    }

    // ================================================================
    // FALLBACK POR REGRAS
    // ================================================================
    private String porRegras(Dados d, String rascunho, String ajuste) {
        String base = limpar(rascunho);

        // 1a frase: quem e a ONG (nome + cidade) + o que ela faz (do rascunho).
        StringBuilder sb = new StringBuilder();
        sb.append("A ").append(d.nome);
        if (temTexto(d.cidade)) sb.append(", de ").append(d.cidade).append(",");
        if (!base.isBlank()) {
            sb.append(" ").append(minusculaInicial(base));
        } else {
            sb.append(d.verificada ? " é uma organização verificada na plataforma"
                    : " é uma organização social que atua na comunidade");
        }
        if (!terminaComPontuacao(sb.toString())) sb.append(".");

        // 2a frase (opcional): necessidades abertas e categorias que costuma pedir.
        boolean encurtar = pediuEncurtar(ajuste);
        if (!encurtar && d.necessidadesAbertas > 0) {
            sb.append(" Atualmente com ").append(d.necessidadesAbertas)
              .append(d.necessidadesAbertas == 1 ? " necessidade aberta" : " necessidades abertas");
            if (!d.categoriasPedidas.isEmpty()) {
                sb.append(" (").append(String.join(", ", d.categoriasPedidas).toLowerCase()).append(")");
            }
            sb.append(", contamos com a sua ajuda para continuar transformando vidas.");
        } else if (!encurtar) {
            sb.append(" Contamos com a sua ajuda para continuar transformando vidas.");
        }

        return sb.toString().trim();
    }

    // Ajuste minimo no fallback: pedido de "mais curto/resumido" ou similar.
    private boolean pediuEncurtar(String ajuste) {
        if (ajuste == null || ajuste.isBlank()) return false;
        String a = normalizar(ajuste);
        return a.contains("curto") || a.contains("curta") || a.contains("resum")
                || a.contains("menor") || a.contains("breve") || a.contains("concis");
    }

    // Colapsa espacos/quebras e apara.
    private String limpar(String t) {
        if (t == null) return "";
        return t.replaceAll("[\\r\\n]+", " ").replaceAll("\\s{2,}", " ").trim();
    }

    // Primeira letra minuscula (para emendar o rascunho depois de "A <Nome>, de <cidade>,").
    private String minusculaInicial(String t) {
        if (t == null || t.isBlank()) return "";
        return Character.toLowerCase(t.charAt(0)) + t.substring(1);
    }

    private boolean terminaComPontuacao(String t) {
        if (t == null || t.isBlank()) return false;
        char c = t.charAt(t.length() - 1);
        return c == '.' || c == '!' || c == '?';
    }

    private boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().trim();
    }
}
