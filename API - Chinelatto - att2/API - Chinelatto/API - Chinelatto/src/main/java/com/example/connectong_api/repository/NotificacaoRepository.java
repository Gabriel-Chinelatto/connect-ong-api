package com.example.connectong_api.repository;

import com.example.connectong_api.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

    List<Notificacao> findByUsuarioIdOrderByDataCriacaoDesc(Long usuarioId);

    long countByUsuarioIdAndLidaFalse(Long usuarioId);
}
