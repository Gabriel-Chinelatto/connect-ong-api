package com.example.connectong_api.repository;

import com.example.connectong_api.model.Campanha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CampanhaRepository extends JpaRepository<Campanha, Long> {

    List<Campanha> findByOngIdOrderByIdDesc(Long ongId);

    List<Campanha> findByEncerradaFalseOrderByIdDesc();

    List<Campanha> findByDestaqueTrueAndEncerradaFalseOrderByIdDesc();

    long countByOngIdAndEncerradaTrue(Long ongId);

    // Campanhas concluidas (encerrada=true) agrupadas por ONG, numa unica
    // query. Retorna pares [ongId, total]. Usado no ranking.
    @Query("SELECT c.ong.id, COUNT(c) FROM Campanha c "
            + "WHERE c.encerrada = true GROUP BY c.ong.id")
    List<Object[]> contarConcluidasPorOng();

    // -------------------------------------------------------------------------
    // Variantes com JOIN FETCH da ONG (contra N+1). Campanha->Ong e @ManyToOne
    // (EAGER): sem o fetch, o Hibernate dispara UMA CONSULTA POR ONG para
    // preencher c.getOng(), usado no CampanhaResponseDTO (nome da ONG). Com o
    // banco longe do servidor (~600ms por ida) isso custava ~2,9s.
    // A ordenacao de cada variante e a MESMA do metodo que ela substitui.
    // -------------------------------------------------------------------------

    @Query("SELECT c FROM Campanha c LEFT JOIN FETCH c.ong")
    List<Campanha> findAllComOng();

    @Query("SELECT c FROM Campanha c LEFT JOIN FETCH c.ong WHERE c.encerrada = false ORDER BY c.id DESC")
    List<Campanha> findAbertasComOng();

    @Query("SELECT c FROM Campanha c LEFT JOIN FETCH c.ong o WHERE o.id = :ongId ORDER BY c.id DESC")
    List<Campanha> findByOngIdComOng(Long ongId);
}
