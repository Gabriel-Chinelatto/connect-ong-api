package com.example.connectong_api.repository;

import com.example.connectong_api.model.AvaliacaoDoador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvaliacaoDoadorRepository extends JpaRepository<AvaliacaoDoador, Long> {

    List<AvaliacaoDoador> findByDoadorIdOrderByCriadoEmDesc(Long doadorId);

    // Chave do upsert: uma unica avaliacao por par ONG + doador.
    Optional<AvaliacaoDoador> findByOngIdAndDoadorId(Long ongId, Long doadorId);
}
