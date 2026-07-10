package com.example.connectong_api.repository;

import com.example.connectong_api.model.Interesse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InteresseRepository extends JpaRepository<Interesse, Long> {

    // Interesses demonstrados por um doador
    List<Interesse> findByDoadorId(Long doadorId);

    // Interesses recebidos nas necessidades de uma ONG
    List<Interesse> findByNecessidadeOngId(Long ongId);

    // Interesses de uma necessidade especifica
    List<Interesse> findByNecessidadeId(Long necessidadeId);

    // Total de matches num determinado status (ex.: "ACEITO")
    long countByStatus(String status);

    // Matches de um doador num determinado status (ex.: "CONCLUIDO")
    long countByDoadorIdAndStatus(Long doadorId, String status);

    // Pendencias de prestacao de contas de UMA ONG: matches CONCLUIDOS que
    // ainda nao receberam nenhuma prestacao.
    @Query("SELECT i FROM Interesse i WHERE i.status = 'CONCLUIDO' "
            + "AND i.necessidade.ong.id = :ongId "
            + "AND NOT EXISTS (SELECT p FROM Prestacao p WHERE p.interesse = i)")
    List<Interesse> concluidosSemPrestacaoPorOng(@Param("ongId") Long ongId);

    // Todas as pendencias da plataforma (para a penalidade de transparencia
    // no ranking, agrupadas por ONG em memoria).
    @Query("SELECT i FROM Interesse i WHERE i.status = 'CONCLUIDO' "
            + "AND NOT EXISTS (SELECT p FROM Prestacao p WHERE p.interesse = i)")
    List<Interesse> concluidosSemPrestacao();

    // Existe um match CONCLUIDO entre este doador e esta ONG? E o LASTRO que
    // habilita a avaliacao nos dois sentidos (doador->ONG e ONG->doador): so
    // avalia quem de fato concluiu uma doacao com o outro lado — evita "review
    // bombing" (nota sem relacao real) na reputacao publica.
    @Query("SELECT COUNT(i) > 0 FROM Interesse i "
            + "WHERE i.doador.id = :doadorId AND i.necessidade.ong.id = :ongId "
            + "AND i.status = 'CONCLUIDO'")
    boolean existeConcluidoEntre(@Param("doadorId") Long doadorId,
                                 @Param("ongId") Long ongId);
}
