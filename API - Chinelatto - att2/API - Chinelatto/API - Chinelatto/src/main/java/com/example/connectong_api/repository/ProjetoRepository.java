package com.example.connectong_api.repository;

import com.example.connectong_api.model.Projeto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjetoRepository
        extends JpaRepository<Projeto, Long> {
}