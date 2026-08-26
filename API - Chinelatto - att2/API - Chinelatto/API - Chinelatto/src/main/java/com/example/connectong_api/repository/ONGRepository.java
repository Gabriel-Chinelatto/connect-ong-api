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

    // LISTAGEM LEVE (GET /ongs). Nao usa findAll de proposito: o findAll traz a
    // ENTIDADE inteira, e a entidade carrega capa_base64 + logo_base64. Com a
    // demonstracao ilustrada isso significa LER ~78 MB do banco a cada listagem
    // (2.000 ONGs x ~39 KB) so para jogar fora na hora de montar o DTO — a
    // resposta nao leva imagem nenhuma. O lixo que isso gera derrubava o
    // desempenho das telas seguintes (o /necessidades chegou a 8 s logo depois
    // de um /ongs). Aqui vem so as colunas que a listagem realmente usa.
    // ORDER BY id: mesma ordem que o findAll devolvia, para a resposta nao mudar.
    // COALESCE: 15 ONGs antigas tem verificada/total_avaliacoes NULL no banco, e a
    // ENTIDADE devolvia o valor do inicializador do campo (false/0). Sem o coalesce
    // a projecao devolveria null e a resposta mudaria para essas ONGs.
    @Query("select o.id, o.nome, o.email, o.telefone, o.cidade, o.descricao, o.cnpj, coalesce(o.verificada, false), coalesce(o.notaMedia, 0.0), coalesce(o.totalAvaliacoes, 0), o.latitude, o.longitude "
         + "from Ong o where o.dataExclusao is null order by o.id")
    List<Object[]> listagemLeve();

    @Query("select o.id, o.nome, o.email, o.telefone, o.cidade, o.descricao, o.cnpj, coalesce(o.verificada, false), coalesce(o.notaMedia, 0.0), coalesce(o.totalAvaliacoes, 0), o.latitude, o.longitude "
         + "from Ong o where o.dataExclusao is null "
         + "and lower(o.nome) like lower(concat('%', :nome, '%')) order by o.id")
    List<Object[]> listagemLevePorNome(@Param("nome") String nome);
}