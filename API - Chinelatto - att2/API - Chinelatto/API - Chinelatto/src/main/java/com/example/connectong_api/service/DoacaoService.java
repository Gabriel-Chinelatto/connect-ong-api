package com.example.connectong_api.service;

import com.example.connectong_api.dto.DoacaoResponseDTO;
import com.example.connectong_api.exception.AcessoNegadoException;
import com.example.connectong_api.model.Doacao;
import com.example.connectong_api.repository.DoacaoRepository;
import com.example.connectong_api.util.Categorias;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRUD basico de itens de doacao (nome, quantidade, categoria, tipo, urgencia).
 * Valida que nome nao seja vazio e que a quantidade seja positiva tanto na
 * criacao quanto na atualizacao; retorna 404 quando o item nao existe.
 */
@Service
public class DoacaoService {

    @Autowired
    private DoacaoRepository repository;

    // =========================
    // LISTAR
    // =========================

    // Catalogo global (usado pelo painel da ONG no desktop).
    public List<DoacaoResponseDTO> listar() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // "Minhas Doacoes": so os itens do proprio doador (derivado do token).
    public List<DoacaoResponseDTO> listarPorDoador(Long doadorId) {
        return repository.findByDoadorId(doadorId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private DoacaoResponseDTO toDTO(Doacao d) {
        return new DoacaoResponseDTO(
                d.getId(),
                d.getNome(),
                d.getDescricao(),
                d.getQuantidade(),
                d.getCategoria(),
                d.getTipo(),
                d.getUrgente(),
                d.getNovo()
        );
    }

    // =========================
    // CRIAR
    // =========================

    // doadorId vem do token (nunca do cliente): define o dono do item.
    public ResponseEntity<?> criar(Doacao doacao, Long doadorId) {

        if (doacao.getNome() == null || doacao.getNome().isBlank()) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Nome da doação é obrigatório");

            return ResponseEntity.badRequest().body(erro);
        }

        if (doacao.getQuantidade() == null || doacao.getQuantidade() <= 0) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Quantidade inválida");

            return ResponseEntity.badRequest().body(erro);
        }

        doacao.setDoadorId(doadorId);
        doacao.setCategoria(Categorias.normalizar(doacao.getCategoria()));
        Doacao nova = repository.save(doacao);

        return ResponseEntity.ok(toDTO(nova));
    }

    // =========================
    // ATUALIZAR
    // =========================

    public ResponseEntity<?> atualizar(Long id, Doacao doacaoAtualizada, Long solicitanteId) {

        Doacao doacao = repository.findById(id).orElse(null);

        if (doacao == null) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Doação não encontrada");

            return ResponseEntity.status(404).body(erro);
        }

        // So o dono pode editar (itens legados sem dono seguem editaveis).
        if (doacao.getDoadorId() != null
                && !doacao.getDoadorId().equals(solicitanteId)) {
            throw new AcessoNegadoException(
                    "Você não pode editar a doação de outro usuário.");
        }

        if (doacaoAtualizada.getNome() == null || doacaoAtualizada.getNome().isBlank()) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Nome da doação é obrigatório");

            return ResponseEntity.badRequest().body(erro);
        }

        if (doacaoAtualizada.getQuantidade() == null || doacaoAtualizada.getQuantidade() <= 0) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Quantidade inválida");

            return ResponseEntity.badRequest().body(erro);
        }

        doacao.setNome(doacaoAtualizada.getNome());
        doacao.setDescricao(doacaoAtualizada.getDescricao());
        doacao.setQuantidade(doacaoAtualizada.getQuantidade());
        doacao.setCategoria(Categorias.normalizar(doacaoAtualizada.getCategoria()));
        doacao.setTipo(doacaoAtualizada.getTipo());
        doacao.setUrgente(doacaoAtualizada.getUrgente());
        doacao.setNovo(doacaoAtualizada.getNovo());

        Doacao atualizada = repository.save(doacao);

        return ResponseEntity.ok(toDTO(atualizada));
    }

    // =========================
    // DELETAR
    // =========================

    public ResponseEntity<?> deletar(Long id, Long solicitanteId) {

        Doacao doacao = repository.findById(id).orElse(null);

        if (doacao == null) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Doação não encontrada");

            return ResponseEntity.status(404).body(erro);
        }

        // So o dono pode excluir (itens legados sem dono seguem removiveis).
        if (doacao.getDoadorId() != null
                && !doacao.getDoadorId().equals(solicitanteId)) {
            throw new AcessoNegadoException(
                    "Você não pode excluir a doação de outro usuário.");
        }

        repository.delete(doacao);

        return ResponseEntity.noContent().build();
    }
}