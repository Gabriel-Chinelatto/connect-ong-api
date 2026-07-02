package com.example.connectong_api.repository;

import com.example.connectong_api.model.Reacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReacaoRepository extends JpaRepository<Reacao, Long> {

    // A reacao de um usuario numa mensagem (no maximo uma).
    Optional<Reacao> findByMensagemIdAndUsuarioId(Long mensagemId, Long usuarioId);

    // Reacoes de uma mensagem (0..2 num chat de 2 participantes).
    List<Reacao> findByMensagemId(Long mensagemId);

    // Carrega em lote as reacoes de varias mensagens (para a listagem do chat).
    List<Reacao> findByMensagemIdIn(List<Long> mensagemIds);
}
