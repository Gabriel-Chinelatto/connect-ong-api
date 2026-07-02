package com.example.connectong_api.repository;

import com.example.connectong_api.model.Ong;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ONGRepository extends JpaRepository<Ong, Long> {
    List<Ong> findByNomeContainingIgnoreCase(String nome);

    // ONGs ativas (nao excluidas) — para as estatisticas publicas.
    long countByDataExclusaoIsNull();
}