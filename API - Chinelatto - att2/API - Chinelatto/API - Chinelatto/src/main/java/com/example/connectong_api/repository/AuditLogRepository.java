package com.example.connectong_api.repository;

import com.example.connectong_api.model.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);

    List<AuditLog> findByAcaoOrderByCriadoEmDesc(String acao);

    // Lista os mais recentes primeiro (com limite via Pageable).
    List<AuditLog> findAllByOrderByCriadoEmDesc(Pageable pageable);
}
