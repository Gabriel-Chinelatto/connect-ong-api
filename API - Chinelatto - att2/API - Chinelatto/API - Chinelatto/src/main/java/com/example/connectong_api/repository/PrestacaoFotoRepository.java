package com.example.connectong_api.repository;

import com.example.connectong_api.model.PrestacaoFoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrestacaoFotoRepository extends JpaRepository<PrestacaoFoto, Long> {

    List<PrestacaoFoto> findByPrestacaoIdOrderByIdAsc(Long prestacaoId);

    // Fotos de varias prestacoes numa unica query (evita N+1 nas listagens).
    List<PrestacaoFoto> findByPrestacaoIdInOrderByIdAsc(List<Long> prestacaoIds);
}
