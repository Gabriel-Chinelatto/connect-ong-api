package com.example.connectong_api.service;

import com.example.connectong_api.dto.TransparenciaDTO;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.CampanhaRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.PrestacaoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Calcula o indice de transparencia das ONGs a partir de sinais publicos ja
 * existentes (sem nova tabela). Pontuacao 0-100:
 *   - selo verificada ............ 25
 *   - nota media (avaliacoes) .... ate 25  (nota/5 * 25)
 *   - prestacoes de contas ....... ate 25  (min(qtd,5) * 5)
 *   - campanhas concluidas ....... ate 25  (min(qtd,5) * 5)
 * Niveis: OURO >= 75, PRATA >= 45, senao BRONZE.
 */
@Service
public class TransparenciaService {

    @Autowired private ONGRepository ongRepository;
    @Autowired private PrestacaoRepository prestacaoRepository;
    @Autowired private CampanhaRepository campanhaRepository;

    public ResponseEntity<?> transparencia(Long ongId) {
        Ong ong = ongRepository.findById(ongId).orElse(null);
        if (ong == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(calcular(ong));
    }

    public TransparenciaDTO calcular(Ong ong) {
        long prestacoes = prestacaoRepository
                .countByInteresseNecessidadeOngId(ong.getId());
        long campanhasConcluidas = campanhaRepository
                .countByOngIdAndEncerradaTrue(ong.getId());
        int score = score(ong, prestacoes, campanhasConcluidas);
        return new TransparenciaDTO(ong, score, nivel(score),
                prestacoes, campanhasConcluidas);
    }

    public int score(Ong ong, long prestacoes, long campanhasConcluidas) {
        int s = 0;
        if (ong.getVerificada()) s += 25;
        s += (int) Math.round(ong.getNotaMedia() / 5.0 * 25);
        s += (int) Math.min(prestacoes, 5) * 5;
        s += (int) Math.min(campanhasConcluidas, 5) * 5;
        return Math.max(0, Math.min(100, s));
    }

    public String nivel(int score) {
        if (score >= 75) return "OURO";
        if (score >= 45) return "PRATA";
        return "BRONZE";
    }

    // Ranking publico: ONGs ordenadas por score desc.
    public List<TransparenciaDTO> ranking(int limite) {
        int lim = limite <= 0 ? 20 : Math.min(limite, 100);
        return ongRepository.findAll().stream()
                .map(this::calcular)
                .sorted(Comparator.comparingInt(TransparenciaDTO::getScore).reversed())
                .limit(lim)
                .collect(Collectors.toList());
    }
}
