package com.example.connectong_api.repository;

import com.example.connectong_api.model.SenhaReset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SenhaResetRepository extends JpaRepository<SenhaReset, Long> {

    // Codigos ainda validos (nao usados) de um usuario — usados para invalidar
    // os anteriores quando um novo codigo e gerado.
    List<SenhaReset> findByUsuarioIdAndUsadoEmIsNull(Long usuarioId);

    // Busca o codigo informado, se ainda nao consumido (o mais recente em caso
    // de residuo historico). A expiracao e conferida no service.
    Optional<SenhaReset> findTopByUsuarioIdAndCodigoAndUsadoEmIsNullOrderByIdDesc(
            Long usuarioId, String codigo);
}
