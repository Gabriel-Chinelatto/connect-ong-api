package com.example.connectong_api.repository;

import com.example.connectong_api.model.DoacaoFinanceira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DoacaoFinanceiraRepository
        extends JpaRepository<DoacaoFinanceira, Long> {

    List<DoacaoFinanceira> findByDoadorIdOrderByDataCriacaoDesc(Long doadorId);

    List<DoacaoFinanceira> findByOngIdOrderByDataCriacaoDesc(Long ongId);

    // Soma total doada (0 quando ainda nao ha doacoes)
    @Query("SELECT COALESCE(SUM(d.valor), 0) FROM DoacaoFinanceira d")
    double somarValores();
}
