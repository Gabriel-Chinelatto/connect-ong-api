package com.example.connectong_api.repository;

import com.example.connectong_api.model.Necessidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NecessidadeRepository extends JpaRepository<Necessidade, Long> {

    // Necessidades de uma ONG especifica
    List<Necessidade> findByOngId(Long ongId);

    // Necessidades por status (ex.: ABERTA)
    List<Necessidade> findByStatus(String status);

    // -------------------------------------------------------------------------
    // Variantes com JOIN FETCH da ONG (contra N+1).
    //
    // A relacao Necessidade->Ong e @ManyToOne (EAGER por padrao): sem o fetch, o
    // Hibernate traz as necessidades numa consulta e depois dispara UMA CONSULTA
    // POR ONG para preencher n.getOng() — que o toDTO usa (nome/cidade/nota...).
    // Eram ~10 idas ao banco na listagem; com o MySQL da escola longe do servidor
    // (~600ms por ida) isso custava ~3,9s. Com JOIN FETCH vem tudo de uma vez.
    //
    // LEFT (e nao INNER) de proposito: necessidade sem ONG continua aparecendo,
    // como antes (o service trata n.getOng() == null).
    // -------------------------------------------------------------------------

    @Query("SELECT n FROM Necessidade n LEFT JOIN FETCH n.ong")
    List<Necessidade> findAllComOng();

    @Query("SELECT n FROM Necessidade n LEFT JOIN FETCH n.ong WHERE n.status = :status")
    List<Necessidade> findByStatusComOng(String status);

    @Query("SELECT n FROM Necessidade n LEFT JOIN FETCH n.ong o WHERE o.id = :ongId")
    List<Necessidade> findByOngIdComOng(Long ongId);
}
