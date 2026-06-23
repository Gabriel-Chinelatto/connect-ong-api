package com.example.connectong_api.repository;

import com.example.connectong_api.model.Mensagem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    // Mensagens de um match, em ordem cronologica.
    List<Mensagem> findByInteresseIdOrderByDataEnvioAsc(Long interesseId);
}
