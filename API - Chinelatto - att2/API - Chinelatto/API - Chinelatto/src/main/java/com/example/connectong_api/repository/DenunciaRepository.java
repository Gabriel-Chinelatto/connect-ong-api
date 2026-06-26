package com.example.connectong_api.repository;

import com.example.connectong_api.model.Denuncia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {

    List<Denuncia> findAllByOrderByDataCriacaoDesc();

    List<Denuncia> findByStatusOrderByDataCriacaoDesc(String status);
}
