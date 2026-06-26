package com.example.connectong_api.repository;

import com.example.connectong_api.model.Campanha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

    List<Campanha> findByOngIdOrderByIdDesc(Long ongId);

    List<Campanha> findByEncerradaFalseOrderByIdDesc();

    List<Campanha> findByDestaqueTrueAndEncerradaFalseOrderByIdDesc();

    long countByOngIdAndEncerradaTrue(Long ongId);

    // Campanhas concluidas (encerrada=true) agrupadas por ONG, numa unica
    // query. Retorna pares [ongId, total]. Usado no ranking.
    @Query("SELECT c.ong.id, COUNT(c) FROM Campanha c "
            + "WHERE c.encerrada = true GROUP BY c.ong.id")
    List<Object[]> contarConcluidasPorOng();
}
