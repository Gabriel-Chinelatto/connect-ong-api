package com.example.connectong_api.repository;

import com.example.connectong_api.model.Favorito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoritoRepository extends JpaRepository<Favorito, Long> {

    List<Favorito> findByUsuarioIdOrderByDataCriacaoDesc(Long usuarioId);

    List<Favorito> findByUsuarioIdAndTipo(Long usuarioId, String tipo);

    Optional<Favorito> findByUsuarioIdAndTipoAndAlvoId(Long usuarioId, String tipo, Long alvoId);

    // Quem favoritou um determinado alvo (ex.: para notificar seguidores)
    List<Favorito> findByTipoAndAlvoId(String tipo, Long alvoId);
}
