package com.example.connectong_api.repository;

import com.example.connectong_api.model.Doacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoacaoRepository extends JpaRepository<Doacao, Long> {

    // Itens de doacao de um doador especifico ("Minhas Doacoes").
    List<Doacao> findByDoadorId(Long doadorId);
}