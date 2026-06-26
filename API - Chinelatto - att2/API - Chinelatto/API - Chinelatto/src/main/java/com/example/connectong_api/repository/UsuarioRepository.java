package com.example.connectong_api.repository;

import com.example.connectong_api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    Optional<Usuario> findByOngId(Long ongId);

    long countByTipo(String tipo);

}