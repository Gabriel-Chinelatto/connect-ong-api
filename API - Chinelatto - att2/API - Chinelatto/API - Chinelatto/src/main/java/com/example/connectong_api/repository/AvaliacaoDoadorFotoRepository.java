package com.example.connectong_api.repository;

import com.example.connectong_api.model.AvaliacaoDoadorFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AvaliacaoDoadorFotoRepository
        extends JpaRepository<AvaliacaoDoadorFoto, Long> {

    List<AvaliacaoDoadorFoto> findByAvaliacaoDoadorIdOrderByIdAsc(Long avaliacaoDoadorId);

    // Fotos de varias avaliacoes numa unica query (evita N+1 na listagem publica).
    List<AvaliacaoDoadorFoto> findByAvaliacaoDoadorIdInOrderByIdAsc(
            List<Long> avaliacaoDoadorIds);

    @Transactional
    void deleteByAvaliacaoDoadorId(Long avaliacaoDoadorId);
}
