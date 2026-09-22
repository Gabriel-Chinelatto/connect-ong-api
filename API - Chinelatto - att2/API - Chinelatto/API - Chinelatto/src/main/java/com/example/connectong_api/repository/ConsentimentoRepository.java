package com.example.connectong_api.repository;

import com.example.connectong_api.model.Consentimento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsentimentoRepository extends JpaRepository<Consentimento, Long> {

    List<Consentimento> findByUsuarioIdOrderByAceitoEmDesc(Long usuarioId);
}
