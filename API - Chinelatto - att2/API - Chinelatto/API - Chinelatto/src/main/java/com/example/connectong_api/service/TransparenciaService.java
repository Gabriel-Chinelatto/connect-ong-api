package com.example.connectong_api.service;

import com.example.connectong_api.dto.TransparenciaDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.CampanhaRepository;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.PrestacaoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calcula o indice de transparencia das ONGs a partir de sinais publicos ja
 * existentes (sem nova tabela). Pontuacao 0-100:
 *   - selo verificada ............ 25
 *   - nota media (avaliacoes) .... ate 25  (nota/5 * 25)
 *   - prestacoes de contas ....... ate 25  (min(qtd,5) * 5)
 *   - campanhas concluidas ....... ate 25  (min(qtd,5) * 5)
 * PENALIDADE DE TRANSPARENCIA: cada pendencia DEFINITIVA (match CONCLUIDO ha
 * mais de 10 dias sem nenhuma prestacao de contas) desconta 5 pontos do score,
 * com piso em 0. Calculado on-read (sem cron): a pendencia vira definitiva
 * automaticamente quando o prazo estoura.
 * Niveis: OURO >= 75, PRATA >= 45, senao BRONZE.
 */
@Service
public class TransparenciaService {

    // Prazo (dias) para a ONG prestar contas de um match CONCLUIDO.
    public static final int PRAZO_PRESTACAO_DIAS = 10;
    // Pontos descontados do score por pendencia definitiva.
    public static final int PENALIDADE_PENDENCIA = 5;

    @Autowired private ONGRepository ongRepository;
    @Autowired private PrestacaoRepository prestacaoRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private InteresseRepository interesseRepository;

    public ResponseEntity<?> transparencia(Long ongId) {
        Ong ong = ongRepository.findById(ongId).orElse(null);
        // ONG inexistente OU excluida (soft-delete) -> 404.
        if (ong == null || ong.getDataExclusao() != null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(calcular(ong));
    }

    public TransparenciaDTO calcular(Ong ong) {
        long prestacoes = prestacaoRepository
                .countByInteresseNecessidadeOngId(ong.getId());
        long campanhasConcluidas = campanhaRepository
                .countByOngIdAndEncerradaTrue(ong.getId());
        long pendenciasDefinitivas = interesseRepository
                .concluidosSemPrestacaoPorOng(ong.getId()).stream()
                .filter(i -> ehDefinitiva(i.getDataConclusao()))
                .count();
        int score = score(ong, prestacoes, campanhasConcluidas, pendenciasDefinitivas);
        return new TransparenciaDTO(ong, score, nivel(score),
                prestacoes, campanhasConcluidas);
    }

    // Uma pendencia e DEFINITIVA quando o prazo de 10 dias desde a conclusao
    // do match ja estourou (>= 10 dias corridos sem prestacao).
    public boolean ehDefinitiva(LocalDateTime dataConclusao) {
        if (dataConclusao == null) return false;
        return ChronoUnit.DAYS.between(dataConclusao, LocalDateTime.now())
                >= PRAZO_PRESTACAO_DIAS;
    }

    public int score(Ong ong, long prestacoes, long campanhasConcluidas,
                     long pendenciasDefinitivas) {
        int s = 0;
        if (ong.getVerificada()) s += 25;
        s += (int) Math.round(ong.getNotaMedia() / 5.0 * 25);
        s += (int) Math.min(prestacoes, 5) * 5;
        s += (int) Math.min(campanhasConcluidas, 5) * 5;
        // Penalidade: -5 pontos por pendencia definitiva (piso 0 garantido
        // pelo Math.max abaixo).
        s -= (int) pendenciasDefinitivas * PENALIDADE_PENDENCIA;
        return Math.max(0, Math.min(100, s));
    }

    public String nivel(int score) {
        if (score >= 75) return "OURO";
        if (score >= 45) return "PRATA";
        return "BRONZE";
    }

    // Ranking publico: ONGs ordenadas por score desc.
    //
    // Antes: findAll() + 2 counts POR ONG (1 + 2N queries, sendo uma um join de
    // 3 niveis). Agora: 4 queries no total (ONGs + prestacoes agrupadas +
    // campanhas agrupadas + pendencias), com as contagens resolvidas em memoria.
    @Transactional
    public List<TransparenciaDTO> ranking(int limite) {
        int lim = limite <= 0 ? 20 : Math.min(limite, 100);

        Map<Long, Long> prestacoesPorOng = paraMapa(prestacaoRepository.contarPrestacoesPorOng());
        Map<Long, Long> campanhasPorOng = paraMapa(campanhaRepository.contarConcluidasPorOng());
        Map<Long, Long> pendenciasPorOng = pendenciasDefinitivasPorOng();

        List<TransparenciaDTO> ranking = ongRepository.findAll().stream()
                .filter(ong -> ong.getDataExclusao() == null) // fora as ONGs excluidas
                .map(ong -> {
                    long prestacoes = prestacoesPorOng.getOrDefault(ong.getId(), 0L);
                    long campanhas = campanhasPorOng.getOrDefault(ong.getId(), 0L);
                    long pendencias = pendenciasPorOng.getOrDefault(ong.getId(), 0L);
                    int score = score(ong, prestacoes, campanhas, pendencias);
                    return new TransparenciaDTO(ong, score, nivel(score),
                            prestacoes, campanhas);
                })
                .sorted(Comparator.comparingInt(TransparenciaDTO::getScore).reversed())
                .limit(lim)
                .collect(Collectors.toList());

        // Streak do Top 1: best-effort — qualquer falha aqui NUNCA derruba o
        // ranking (a resposta sai sem o diasNoTopo, so isso).
        try {
            atualizarStreakTop1(ranking);
        } catch (Exception ignorado) {
            // best-effort
        }

        return ranking;
    }

    // Mantem as colunas ong.top1_desde / ong.ultimo_reinado_dias em dia e
    // preenche diasNoTopo no item #1 do ranking:
    //  - #1 sem top1_desde  -> abre o reinado (top1_desde = now);
    //  - #1 mudou           -> fecha o reinado do anterior (ultimo_reinado_dias
    //                          = dias do reinado, zera o top1_desde dele).
    private void atualizarStreakTop1(List<TransparenciaDTO> ranking) {
        if (ranking.isEmpty()) return;

        TransparenciaDTO top1 = ranking.get(0);
        Ong atual = ongRepository.findById(top1.getOngId()).orElse(null);
        if (atual == null) return;

        LocalDateTime agora = LocalDateTime.now();

        // Fecha o reinado de quem NAO e mais o #1 (normalmente 0 ou 1 ONG).
        for (Ong reinante : ongRepository.findByTop1DesdeIsNotNull()) {
            if (reinante.getId().equals(atual.getId())) continue;
            int dias = (int) Math.max(1,
                    ChronoUnit.DAYS.between(reinante.getTop1Desde(), agora));
            reinante.setUltimoReinadoDias(dias);
            reinante.setTop1Desde(null);
            ongRepository.save(reinante);
        }

        // Abre o reinado do novo #1 (se ainda nao estava aberto).
        if (atual.getTop1Desde() == null) {
            atual.setTop1Desde(agora);
            ongRepository.save(atual);
        }

        top1.setDiasNoTopo(diasNoTopo(atual.getTop1Desde()));
    }

    // Dias no topo, minimo 1 (quem acabou de assumir ja conta 1 dia).
    public int diasNoTopo(LocalDateTime top1Desde) {
        if (top1Desde == null) return 1;
        return (int) Math.max(1,
                ChronoUnit.DAYS.between(top1Desde, LocalDateTime.now()) + 1);
    }

    // Pendencias definitivas agrupadas por ONG, resolvidas em memoria.
    private Map<Long, Long> pendenciasDefinitivasPorOng() {
        Map<Long, Long> mapa = new HashMap<>();
        for (Interesse i : interesseRepository.concluidosSemPrestacao()) {
            if (!ehDefinitiva(i.getDataConclusao())) continue;
            Long ongId = (i.getNecessidade() != null && i.getNecessidade().getOng() != null)
                    ? i.getNecessidade().getOng().getId() : null;
            if (ongId == null) continue;
            mapa.merge(ongId, 1L, Long::sum);
        }
        return mapa;
    }

    // Converte os pares [ongId, total] das queries agregadas em um Map.
    private Map<Long, Long> paraMapa(List<Object[]> linhas) {
        Map<Long, Long> mapa = new HashMap<>();
        for (Object[] linha : linhas) {
            if (linha[0] == null) continue; // ignora ONG nula (dados orfaos)
            mapa.put(((Number) linha[0]).longValue(), ((Number) linha[1]).longValue());
        }
        return mapa;
    }
}
