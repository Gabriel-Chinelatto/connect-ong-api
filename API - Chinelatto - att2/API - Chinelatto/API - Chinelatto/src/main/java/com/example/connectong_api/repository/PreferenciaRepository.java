package com.example.connectong_api.repository;

import com.example.connectong_api.model.Preferencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PreferenciaRepository extends JpaRepository<Preferencia, Long> {

    Optional<Preferencia> findByUsuarioId(Long usuarioId);
}
