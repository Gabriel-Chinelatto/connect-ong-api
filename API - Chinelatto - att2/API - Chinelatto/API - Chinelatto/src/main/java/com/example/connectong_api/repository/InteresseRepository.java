package com.example.connectong_api.repository;

import com.example.connectong_api.model.Interesse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InteresseRepository extends JpaRepository<Interesse, Long> {

    // Interesses demonstrados por um doador
    List<Interesse> findByDoadorId(Long doadorId);

    // Interesses recebidos nas necessidades de uma ONG
    List<Interesse> findByNecessidadeOngId(Long ongId);

    // Interesses de uma necessidade especifica
    List<Interesse> findByNecessidadeId(Long necessidadeId);
}
