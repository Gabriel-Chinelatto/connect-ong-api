package com.example.connectong_api.repository;

import com.example.connectong_api.model.Bloqueio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BloqueioRepository extends JpaRepository<Bloqueio, Long> {

    /** Bloqueios feitos por uma ONG (mais recentes primeiro). */
    List<Bloqueio> findByOngIdOrderByCriadoEmDesc(Long ongId);

    /** Bloqueios que ATINGEM um doador (para excluir essas ONGs do feed dele). */
    List<Bloqueio> findByDoadorId(Long doadorId);

    Optional<Bloqueio> findByOngIdAndDoadorId(Long ongId, Long doadorId);

    boolean existsByOngIdAndDoadorId(Long ongId, Long doadorId);
}
