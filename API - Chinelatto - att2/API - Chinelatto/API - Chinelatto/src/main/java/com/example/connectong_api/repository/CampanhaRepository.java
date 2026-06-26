package com.example.connectong_api.repository;

import com.example.connectong_api.model.Campanha;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

    List<Campanha> findByOngIdOrderByIdDesc(Long ongId);

    List<Campanha> findByEncerradaFalseOrderByIdDesc();

    List<Campanha> findByDestaqueTrueAndEncerradaFalseOrderByIdDesc();

    long countByOngIdAndEncerradaTrue(Long ongId);
}
