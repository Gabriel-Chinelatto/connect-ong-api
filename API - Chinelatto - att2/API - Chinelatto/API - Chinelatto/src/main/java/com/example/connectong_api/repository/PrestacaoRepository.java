package com.example.connectong_api.repository;

import com.example.connectong_api.model.Prestacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PrestacaoRepository extends JpaRepository<Prestacao, Long> {

    List<Prestacao> findByInteresseIdOrderByDataCriacaoDesc(Long interesseId);

    // Prestacoes de uma ONG: prestacao -> interesse -> necessidade -> ong
    List<Prestacao> findByInteresseNecessidadeOngIdOrderByDataCriacaoDesc(Long ongId);
}
