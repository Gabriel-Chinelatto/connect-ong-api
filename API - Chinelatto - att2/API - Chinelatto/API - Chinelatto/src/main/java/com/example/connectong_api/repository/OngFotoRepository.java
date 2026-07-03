package com.example.connectong_api.repository;

import com.example.connectong_api.model.OngFoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OngFotoRepository extends JpaRepository<OngFoto, Long> {

    List<OngFoto> findByOngIdOrderByIdAsc(Long ongId);

    void deleteByOngId(Long ongId);
}
