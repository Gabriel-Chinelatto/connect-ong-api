package com.example.connectong_api.repository;

import com.example.connectong_api.model.Atividade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AtividadeRepository extends JpaRepository<Atividade, Long> {

    // Feed global: mais recentes primeiro (limite via Pageable).
    List<Atividade> findAllByOrderByDataCriacaoDesc(Pageable pageable);

    // Feed de uma ONG especifica (mais recentes primeiro).
    List<Atividade> findByOngIdOrderByDataCriacaoDesc(Long ongId, Pageable pageable);
}
