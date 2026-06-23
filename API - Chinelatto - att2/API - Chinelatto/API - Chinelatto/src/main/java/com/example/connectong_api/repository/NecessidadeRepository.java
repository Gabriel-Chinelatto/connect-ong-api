package com.example.connectong_api.repository;

import com.example.connectong_api.model.Necessidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NecessidadeRepository extends JpaRepository<Necessidade, Long> {

    // Necessidades de uma ONG especifica
    List<Necessidade> findByOngId(Long ongId);

    // Necessidades por status (ex.: ABERTA)
    List<Necessidade> findByStatus(String status);
}
