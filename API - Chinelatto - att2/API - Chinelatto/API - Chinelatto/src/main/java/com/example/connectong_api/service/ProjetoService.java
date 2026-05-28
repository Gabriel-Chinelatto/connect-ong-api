package com.example.connectong_api.service;

import com.example.connectong_api.dto.ProjetoResponseDTO;
import com.example.connectong_api.model.Projeto;
import com.example.connectong_api.repository.ProjetoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProjetoService {

    @Autowired
    private ProjetoRepository repository;

    // =========================
    // LISTAR
    // =========================
    public List<ProjetoResponseDTO> listar() {

        List<Projeto> lista =
                repository.findAll();

        return lista.stream()
                .map(projeto -> new ProjetoResponseDTO(
                        projeto.getId(),
                        projeto.getTitulo(),
                        projeto.getDescricao(),
                        projeto.getMetaValor(),
                        projeto.getOng() != null
                                ? projeto.getOng().getId()
                                : null
                ))
                .collect(Collectors.toList());
    }

    // =========================
    // CRIAR
    // =========================
    public ResponseEntity<?> criar(
            Projeto projeto
    ) {

        // valida título
        if (projeto.getTitulo() == null ||
                projeto.getTitulo().isEmpty()) {

            return erro(
                    "Título do projeto é obrigatório"
            );
        }

        // valida meta
        if (projeto.getMetaValor() == null ||
                projeto.getMetaValor() <= 0) {

            return erro(
                    "Meta do projeto inválida"
            );
        }

        Projeto novo =
                repository.save(projeto);

        ProjetoResponseDTO resposta =
                new ProjetoResponseDTO(
                        novo.getId(),
                        novo.getTitulo(),
                        novo.getDescricao(),
                        novo.getMetaValor(),
                        novo.getOng() != null
                                ? novo.getOng().getId()
                                : null
                );

        return ResponseEntity.ok(
                resposta
        );
    }

    // =========================
    // ERRO PADRÃO
    // =========================
    private ResponseEntity<?> erro(
            String mensagem
    ) {

        Map<String, String> erro =
                new HashMap<>();

        erro.put("erro", mensagem);

        return ResponseEntity
                .badRequest()
                .body(erro);
    }
}