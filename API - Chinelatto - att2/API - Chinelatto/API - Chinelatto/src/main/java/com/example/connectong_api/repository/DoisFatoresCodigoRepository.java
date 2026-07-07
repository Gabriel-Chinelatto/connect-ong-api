package com.example.connectong_api.repository;

import com.example.connectong_api.model.DoisFatoresCodigo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoisFatoresCodigoRepository extends JpaRepository<DoisFatoresCodigo, Long> {

    // Codigos ainda validos (nao usados) de um usuario — invalidados quando um
    // novo codigo e gerado.
    List<DoisFatoresCodigo> findByUsuarioIdAndUsadoEmIsNull(Long usuarioId);

    // Busca o codigo informado, se ainda nao consumido (o mais recente em caso
    // de residuo). A expiracao e conferida no service.
    Optional<DoisFatoresCodigo> findTopByUsuarioIdAndCodigoAndUsadoEmIsNullOrderByIdDesc(
            Long usuarioId, String codigo);
}
