package com.example.connectong_api.service;

import com.example.connectong_api.dto.ResumoImpactoResponseDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.DoacaoFinanceiraRepository;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.PrestacaoRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Resume, para o DOADOR, o IMPACTO/atuacao de uma ONG a partir de numeros REAIS
 * (nome, cidade, verificacao, nota, necessidades abertas, matches concluidos,
 * doacoes PIX, prestacoes de contas). Duas camadas:
 *
 *  1) IA (Groq): monta um paragrafo curto (2-3 frases), caloroso, usando SOMENTE
 *     os numeros fornecidos (nao inventa). Aceita JSON {"resumo":"..."} ou texto puro.
 *  2) FALLBACK por REGRAS (sem chave): monta a frase a partir dos mesmos numeros.
 * Sempre retorna algo. ONG inexistente/excluida -> resumo generico (modo "regras").
 */
@Service
public class ResumoImpactoService {

    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private DoacaoFinanceiraRepository doacaoFinanceiraRepository;
    @Autowired private PrestacaoRepository prestacaoRepository;
    @Autowired private ProvedorIA provedorIA;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Numeros reais coletados para uma ONG. */
    private static class Numeros {
        String nome;
        String cidade;
        boolean verificada;
        double notaMedia;
        int totalAvaliacoes;
        int necessidadesAbertas;
        int matchesConcluidos;
        long doacoesPix;
        long prestacoes;
    }

    public ResumoImpactoResponseDTO resumir(Long ongId) {
        Ong ong = ongId == null ? null : ongRepository.findById(ongId).orElse(null);
        if (ong == null || ong.getDataExclusao() != null) {
            // ONG inexistente/excluida: resumo generico consistente (nao quebra o app).
            return new ResumoImpactoResponseDTO(
                    "Esta ONG não está mais disponível no momento. Explore outras "
                    + "instituições da plataforma e encontre uma causa para apoiar.",
                    "regras");
        }

        Numeros n = coletar(ong);

        if (provedorIA.disponivel()) {
            ResumoImpactoResponseDTO viaIa = tentarIa(n);
            if (viaIa != null) return viaIa;
        }
        return new ResumoImpactoResponseDTO(frasePorRegras(n), "regras");
    }

    // ================================================================
    // COLETA DE NUMEROS REAIS
    // ================================================================
    private Numeros coletar(Ong ong) {
        Numeros n = new Numeros();
        n.nome = ong.getNome();
        n.cidade = ong.getCidade();
        n.verificada = ong.getVerificada();
        n.notaMedia = ong.getNotaMedia();
        n.totalAvaliacoes = ong.getTotalAvaliacoes();

        // Necessidades abertas (status ABERTA/nulo).
        int abertas = 0;
        for (Necessidade nec : necessidadeRepository.findByOngId(ong.getId())) {
            boolean aberta = nec.getStatus() == null || nec.getStatus().isBlank()
                    || "ABERTA".equalsIgnoreCase(nec.getStatus());
            if (aberta) abertas++;
        }
        n.necessidadesAbertas = abertas;

        // Matches CONCLUIDOS ligados a ONG.
        int concluidos = 0;
        for (Interesse i : interesseRepository.findByNecessidadeOngId(ong.getId())) {
            if ("CONCLUIDO".equalsIgnoreCase(i.getStatus())) concluidos++;
        }
        n.matchesConcluidos = concluidos;

        // Doacoes PIX recebidas e prestacoes de contas publicadas.
        n.doacoesPix = doacaoFinanceiraRepository.findByOngIdOrderByDataCriacaoDesc(ong.getId()).size();
        n.prestacoes = prestacaoRepository.countByInteresseNecessidadeOngId(ong.getId());
        return n;
    }

    // ================================================================
    // CAMINHO DA IA
    // ================================================================
    private ResumoImpactoResponseDTO tentarIa(Numeros n) {
        String sistema = "Voce escreve, para um doador, um paragrafo CURTO (2 a 3 frases), "
                + "caloroso e em portugues do Brasil, que resume a atuacao/impacto de uma ONG "
                + "usando SOMENTE os numeros fornecidos. NAO invente nada alem dos numeros. Se "
                + "um numero for zero, nao force. Pode responder em JSON {\"resumo\":\"...\"} "
                + "ou apenas o texto.";

        StringBuilder u = new StringBuilder();
        u.append("Dados reais da ONG:\n");
        u.append("- Nome: ").append(n.nome).append("\n");
        if (temTexto(n.cidade)) u.append("- Cidade: ").append(n.cidade).append("\n");
        u.append("- Verificada: ").append(n.verificada ? "sim" : "nao").append("\n");
        if (n.totalAvaliacoes > 0) {
            u.append("- Nota media: ").append(n.notaMedia)
             .append(" (").append(n.totalAvaliacoes).append(" avaliacoes)\n");
        }
        u.append("- Necessidades abertas: ").append(n.necessidadesAbertas).append("\n");
        u.append("- Doacoes concluidas (matches): ").append(n.matchesConcluidos).append("\n");
        u.append("- Doacoes via PIX recebidas: ").append(n.doacoesPix).append("\n");
        u.append("- Prestacoes de contas publicadas: ").append(n.prestacoes).append("\n");

        List<ProvedorIA.MensagemIA> mensagens = List.of(
                new ProvedorIA.MensagemIA("system", sistema),
                new ProvedorIA.MensagemIA("user", u.toString()));

        // ESCRITA curta ancorada em numeros reais: temperatura media e teto de
        // tokens para 2-3 frases.
        Optional<String> saida = provedorIA.completar(mensagens,
                ProvedorIA.OpcoesIA.de(0.5, 260));
        if (saida.isEmpty()) return null;

        String resumo = extrairResumo(saida.get());
        if (resumo == null || resumo.isBlank()) return null;
        return new ResumoImpactoResponseDTO(resumo, "ia");
    }

    // Aceita JSON {"resumo":"..."} OU texto puro.
    private String extrairResumo(String texto) {
        try {
            int ini = texto.indexOf('{');
            int fim = texto.lastIndexOf('}');
            if (ini >= 0 && fim > ini) {
                JsonNode raiz = objectMapper.readTree(texto.substring(ini, fim + 1));
                String r = raiz.path("resumo").asText("").trim();
                if (!r.isBlank()) return r;
            }
        } catch (Exception ignored) {
            // nao era JSON: usa o texto puro abaixo.
        }
        return texto.trim();
    }

    // ================================================================
    // FALLBACK POR REGRAS
    // ================================================================
    private String frasePorRegras(Numeros n) {
        StringBuilder sb = new StringBuilder();
        sb.append("A ").append(n.nome);
        if (temTexto(n.cidade)) sb.append(", de ").append(n.cidade).append(",");
        sb.append(n.verificada ? " é uma ONG verificada na plataforma" : " atua na plataforma");

        if (n.necessidadesAbertas > 0) {
            sb.append(" e tem ").append(n.necessidadesAbertas)
              .append(n.necessidadesAbertas == 1 ? " necessidade aberta" : " necessidades abertas")
              .append(" no momento");
        }
        sb.append(".");

        // Segunda frase: histórico de apoio recebido.
        boolean temHistorico = n.matchesConcluidos > 0 || n.doacoesPix > 0;
        if (temHistorico) {
            sb.append(" Já recebeu");
            boolean primeiro = true;
            if (n.matchesConcluidos > 0) {
                sb.append(" ").append(n.matchesConcluidos)
                  .append(n.matchesConcluidos == 1 ? " doação de itens concluída" : " doações de itens concluídas");
                primeiro = false;
            }
            if (n.doacoesPix > 0) {
                sb.append(primeiro ? " " : " e ").append(n.doacoesPix)
                  .append(n.doacoesPix == 1 ? " doação via PIX" : " doações via PIX");
            }
            sb.append(" da comunidade");
            if (n.prestacoes > 0) {
                sb.append(", com ").append(n.prestacoes)
                  .append(n.prestacoes == 1 ? " prestação de contas publicada" : " prestações de contas publicadas");
            }
            sb.append(".");
        } else {
            sb.append(" Sua contribuição pode ser o primeiro apoio da comunidade a essa causa.");
        }

        if (n.totalAvaliacoes > 0) {
            sb.append(" Tem nota ").append(formatarNota(n.notaMedia))
              .append(" em ").append(n.totalAvaliacoes)
              .append(n.totalAvaliacoes == 1 ? " avaliação de doadores." : " avaliações de doadores.");
        }
        return sb.toString();
    }

    private String formatarNota(double nota) {
        // Uma casa decimal, com virgula (padrao PT-BR).
        return String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.1f", nota);
    }

    private boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
