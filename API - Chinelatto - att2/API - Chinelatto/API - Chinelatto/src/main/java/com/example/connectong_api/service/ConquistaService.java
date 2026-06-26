package com.example.connectong_api.service;

import com.example.connectong_api.dto.ConquistaDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Conquistas (gamificacao) calculadas a partir de dados ja existentes — sem
 * nova tabela. Retorna sempre a lista COMPLETA (conquistadas + bloqueadas)
 * para o app mostrar o progresso.
 */
@Service
public class ConquistaService {

    @Autowired private DoacaoFinanceiraRepository doacaoFinanceiraRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private ONGRepository ongRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private PrestacaoRepository prestacaoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    // ---------- DOADOR ----------
    public ResponseEntity<?> doador(Long usuarioId) {
        if (usuarioRepository.findById(usuarioId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        long doacoesPix = doacaoFinanceiraRepository
                .findByDoadorIdOrderByDataCriacaoDesc(usuarioId).size();
        long matchesAceitos = interesseRepository.findByDoadorId(usuarioId).stream()
                .filter(i -> "ACEITO".equals(i.getStatus())).count();
        long contribuicoes = doacoesPix + matchesAceitos;

        List<ConquistaDTO> lista = new ArrayList<>();
        lista.add(new ConquistaDTO("PRIMEIRA_DOACAO", "Primeira doacao",
                "Fez sua primeira contribuicao.", contribuicoes >= 1));
        lista.add(new ConquistaDTO("DOADOR_RECORRENTE", "Doador recorrente",
                "Contribuiu 3 vezes ou mais.", contribuicoes >= 3));
        lista.add(new ConquistaDTO("CINCO_DOACOES", "Cinco contribuicoes",
                "Alcancou 5 contribuicoes.", contribuicoes >= 5));
        lista.add(new ConquistaDTO("DEZ_DOACOES", "Dez contribuicoes",
                "Alcancou 10 contribuicoes.", contribuicoes >= 10));
        return ResponseEntity.ok(lista);
    }

    // ---------- ONG ----------
    public ResponseEntity<?> ong(Long ongId) {
        Ong ong = ongRepository.findById(ongId).orElse(null);
        if (ong == null) return ResponseEntity.notFound().build();

        long campanhas = campanhaRepository.findByOngIdOrderByIdDesc(ongId).size();
        long campanhasConcluidas = campanhaRepository.countByOngIdAndEncerradaTrue(ongId);
        long prestacoes = prestacaoRepository.countByInteresseNecessidadeOngId(ongId);
        int avaliacoes = ong.getTotalAvaliacoes();

        List<ConquistaDTO> lista = new ArrayList<>();
        lista.add(new ConquistaDTO("ONG_VERIFICADA", "ONG verificada",
                "Conquistou o selo de verificacao.", ong.getVerificada()));
        lista.add(new ConquistaDTO("PRIMEIRA_CAMPANHA", "Primeira campanha",
                "Lancou sua primeira campanha.", campanhas >= 1));
        lista.add(new ConquistaDTO("PRIMEIRA_PRESTACAO", "Primeira prestacao",
                "Publicou sua primeira prestacao de contas.", prestacoes >= 1));
        lista.add(new ConquistaDTO("PRIMEIRA_AVALIACAO", "Primeira avaliacao",
                "Recebeu sua primeira avaliacao.", avaliacoes >= 1));
        lista.add(new ConquistaDTO("CAMPANHA_CONCLUIDA", "Meta atingida",
                "Concluiu uma campanha (meta atingida).", campanhasConcluidas >= 1));
        return ResponseEntity.ok(lista);
    }
}
