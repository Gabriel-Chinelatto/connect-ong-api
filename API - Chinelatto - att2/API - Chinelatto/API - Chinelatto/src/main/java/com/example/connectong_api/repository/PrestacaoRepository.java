package com.example.connectong_api.repository;

import com.example.connectong_api.model.Prestacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PrestacaoRepository extends JpaRepository<Prestacao, Long> {

    List<Prestacao> findByInteresseIdOrderByDataCriacaoDesc(Long interesseId);

    // Prestacoes de uma ONG: prestacao -> interesse -> necessidade -> ong
    List<Prestacao> findByInteresseNecessidadeOngIdOrderByDataCriacaoDesc(Long ongId);

    long countByInteresseNecessidadeOngId(Long ongId);

    // Quantidade de prestacoes agrupada por ONG (uma unica query, em vez de
    // um count por ONG). Retorna pares [ongId, total]. Usado no ranking.
    @Query("SELECT n.ong.id, COUNT(p) FROM Prestacao p "
            + "JOIN p.interesse i JOIN i.necessidade n GROUP BY n.ong.id")
    List<Object[]> contarPrestacoesPorOng();

    // Prestacoes RECEBIDAS por um doador (prestacao -> interesse -> doador),
    // exibidas no perfil publico do doador.
    List<Prestacao> findByInteresseDoadorIdOrderByDataCriacaoDesc(Long doadorId);
}
