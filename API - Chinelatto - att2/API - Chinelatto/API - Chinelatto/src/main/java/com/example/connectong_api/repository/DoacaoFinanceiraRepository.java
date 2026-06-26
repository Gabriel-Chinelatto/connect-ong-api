package com.example.connectong_api.repository;

import com.example.connectong_api.model.DoacaoFinanceira;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoacaoFinanceiraRepository
        extends JpaRepository<DoacaoFinanceira, Long> {

    List<DoacaoFinanceira> findByDoadorIdOrderByDataCriacaoDesc(Long doadorId);

    List<DoacaoFinanceira> findByOngIdOrderByDataCriacaoDesc(Long ongId);
}
