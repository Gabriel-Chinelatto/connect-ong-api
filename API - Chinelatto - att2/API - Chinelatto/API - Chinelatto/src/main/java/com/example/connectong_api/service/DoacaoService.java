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

@Service
public class DoacaoService {

    @Autowired
    private DoacaoRepository repository;

    // =========================
    // LISTAR
    // =========================
    public List<DoacaoResponseDTO> listar() {

        List<Doacao> lista =
                repository.findAll();

        return lista.stream()
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
    public ResponseEntity<?> criar(
            Doacao doacao
    ) {

        // validações básicas
        if (doacao.getNome() == null ||
                doacao.getNome().isEmpty()) {

            Map<String, String> erro =
                    new HashMap<>();

            erro.put(
                    "erro",
                    "Nome da doação é obrigatório"
            );

            return ResponseEntity
                    .badRequest()
                    .body(erro);
        }

        if (doacao.getQuantidade() == null ||
                doacao.getQuantidade() <= 0) {

            Map<String, String> erro =
                    new HashMap<>();

            erro.put(
                    "erro",
                    "Quantidade inválida"
            );

            return ResponseEntity
                    .badRequest()
                    .body(erro);
        }

        Doacao nova =
                repository.save(doacao);

        DoacaoResponseDTO resposta =
                new DoacaoResponseDTO(
                        nova.getId(),
                        nova.getNome(),
                        nova.getDescricao(),
                        nova.getQuantidade(),
                        nova.getCategoria(),
                        nova.getTipo(),
                        nova.getUrgente(),
                        nova.getNovo()
                );

        return ResponseEntity.ok(
                resposta
        );
    }
}