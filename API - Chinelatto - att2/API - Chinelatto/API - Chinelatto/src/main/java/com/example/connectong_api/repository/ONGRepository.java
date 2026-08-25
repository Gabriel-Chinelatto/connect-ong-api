package com.example.connectong_api.repository;

import com.example.connectong_api.model.Ong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ONGRepository extends JpaRepository<Ong, Long> {
    List<Ong> findByNomeContainingIgnoreCase(String nome);

    // ONGs ativas (nao excluidas) — para as estatisticas publicas.
    long countByDataExclusaoIsNull();

    // ONGs com reinado de Top 1 aberto (em condicoes normais, no maximo uma;
    // lista para tolerar dados inconsistentes e fechar todos os reinados).
    List<Ong> findByTop1DesdeIsNotNull();

    // Imagens servidas por URL (GET /publico/ongs/{id}/logo e .../capa).
    // Projecao de UMA coluna de proposito: buscar a entidade inteira traria
    // junto a capa (dezenas de KB) so para responder o logo. Devolvem lista
    // (0 ou 1 item) porque a coluna pode ser NULL — Optional<String> nao
    // distingue "ONG nao existe" de "ONG sem imagem".
    @Query("select o.logoBase64 from Ong o where o.id = :id and o.dataExclusao is null")
    List<String> logoBase64Da(@Param("id") Long id);

    @Query("select o.capaBase64 from Ong o where o.id = :id and o.dataExclusao is null")
    List<String> capaBase64Da(@Param("id") Long id);
}