package com.example.connectong_api.repository;

import com.example.connectong_api.model.Avaliacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    List<Avaliacao> findByOngIdOrderByDataCriacaoDesc(Long ongId);

    Optional<Avaliacao> findByOngIdAndDoadorId(Long ongId, Long doadorId);
}
