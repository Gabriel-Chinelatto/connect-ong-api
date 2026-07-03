package com.example.connectong_api.repository;

import com.example.connectong_api.model.Ong;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ONGRepository extends JpaRepository<Ong, Long> {
    List<Ong> findByNomeContainingIgnoreCase(String nome);

    // ONGs ativas (nao excluidas) — para as estatisticas publicas.
    long countByDataExclusaoIsNull();

    // ONGs com reinado de Top 1 aberto (em condicoes normais, no maximo uma;
    // lista para tolerar dados inconsistentes e fechar todos os reinados).
    List<Ong> findByTop1DesdeIsNotNull();
}