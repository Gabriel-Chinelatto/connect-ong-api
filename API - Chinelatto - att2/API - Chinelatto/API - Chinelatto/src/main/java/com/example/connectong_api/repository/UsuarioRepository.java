package com.example.connectong_api.repository;

import com.example.connectong_api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByOngId(Long ongId);

    long countByTipo(String tipo);

    // Doadores/ONGs ATIVOS (nao excluidos) — para as estatisticas publicas.
    long countByTipoAndDataExclusaoIsNull(String tipo);

    // Batimento de presenca: marca o momento da ultima atividade do usuario no
    // chat (usado para derivar "online"/"visto por ultimo"). Update direto para
    // nao carregar a entidade a cada poll.
    @Modifying
    @Query("update Usuario u set u.ultimoVisto = :agora where u.id = :id")
    void marcarVisto(@Param("id") Long id, @Param("agora") LocalDateTime agora);

}