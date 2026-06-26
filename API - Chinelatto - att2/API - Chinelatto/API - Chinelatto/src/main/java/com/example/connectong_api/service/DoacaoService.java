package com.example.connectong_api.service;

import com.example.connectong_api.dto.DoacaoResponseDTO;
import com.example.connectong_api.model.Doacao;
import com.example.connectong_api.repository.DoacaoRepository;

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

    public List<DoacaoResponseDTO> listar() {

        return repository.findAll()
                .stream()
                .map(doacao -> new DoacaoResponseDTO(
                        doacao.getId(),
                        doacao.getNome(),
                        doacao.getDescricao(),
                        doacao.getQuantidade(),
                        doacao.getCategoria(),
                        doacao.getTipo(),
                        doacao.getUrgente(),
                        doacao.getNovo()
                ))
                .collect(Collectors.toList());
    }

    // =========================
    // CRIAR
    // =========================

    public ResponseEntity<?> criar(Doacao doacao) {

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

        Doacao nova = repository.save(doacao);

        return ResponseEntity.ok(new DoacaoResponseDTO(
                nova.getId(),
                nova.getNome(),
                nova.getDescricao(),
                nova.getQuantidade(),
                nova.getCategoria(),
                nova.getTipo(),
                nova.getUrgente(),
                nova.getNovo()
        ));
    }

    // =========================
    // ATUALIZAR
    // =========================

    public ResponseEntity<?> atualizar(Long id, Doacao doacaoAtualizada) {

        Doacao doacao = repository.findById(id).orElse(null);

        if (doacao == null) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Doação não encontrada");

            return ResponseEntity.status(404).body(erro);
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
        doacao.setCategoria(doacaoAtualizada.getCategoria());
        doacao.setTipo(doacaoAtualizada.getTipo());
        doacao.setUrgente(doacaoAtualizada.getUrgente());
        doacao.setNovo(doacaoAtualizada.getNovo());

        Doacao atualizada = repository.save(doacao);

        return ResponseEntity.ok(new DoacaoResponseDTO(
                atualizada.getId(),
                atualizada.getNome(),
                atualizada.getDescricao(),
                atualizada.getQuantidade(),
                atualizada.getCategoria(),
                atualizada.getTipo(),
                atualizada.getUrgente(),
                atualizada.getNovo()
        ));
    }

    // =========================
    // DELETAR
    // =========================

    public ResponseEntity<?> deletar(Long id) {

        Doacao doacao = repository.findById(id).orElse(null);

        if (doacao == null) {

            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Doação não encontrada");

            return ResponseEntity.status(404).body(erro);
        }

        repository.delete(doacao);

        return ResponseEntity.noContent().build();
    }
}