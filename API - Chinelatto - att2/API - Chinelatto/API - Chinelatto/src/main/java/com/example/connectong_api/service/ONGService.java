package com.example.connectong_api.service;

import com.example.connectong_api.dto.OngResponseDTO;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.ONGRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ONGService {

    @Autowired
    private ONGRepository repository;

    // =========================
    // LISTAR
    // =========================
    public List<OngResponseDTO> listar(
            String nome
    ) {

        List<Ong> lista;

        if (nome != null &&
                !nome.isEmpty()) {

            lista =
                    repository.findByNomeContainingIgnoreCase(nome);

        } else {

            lista =
                    repository.findAll();
        }

        return lista.stream()
                .map(ong -> new OngResponseDTO(
                        ong.getId(),
                        ong.getNome(),
                        ong.getEmail(),
                        ong.getTelefone(),
                        ong.getCidade(),
                        ong.getDescricao()
                ))
                .collect(Collectors.toList());
    }

    // =========================
    // CRIAR
    // =========================
    public ResponseEntity<?> criar(
            Ong ong
    ) {

        // valida nome
        if (ong.getNome() == null ||
                ong.getNome().isEmpty()) {

            return erro(
                    "Nome da ONG é obrigatório"
            );
        }

        // valida email
        if (ong.getEmail() == null ||
                ong.getEmail().isEmpty()) {

            return erro(
                    "Email é obrigatório"
            );
        }

        Ong nova =
                repository.save(ong);

        OngResponseDTO resposta =
                new OngResponseDTO(
                        nova.getId(),
                        nova.getNome(),
                        nova.getEmail(),
                        nova.getTelefone(),
                        nova.getCidade(),
                        nova.getDescricao()
                );

        return ResponseEntity.ok(
                resposta
        );
    }

    // =========================
    // ATUALIZAR
    // =========================
    public ResponseEntity<?> atualizar(
            Long id,
            Ong ongAtualizada
    ) {

        return repository.findById(id)
                .map(ong -> {

                    ong.setNome(
                            ongAtualizada.getNome()
                    );

                    ong.setEmail(
                            ongAtualizada.getEmail()
                    );

                    ong.setTelefone(
                            ongAtualizada.getTelefone()
                    );

                    ong.setCidade(
                            ongAtualizada.getCidade()
                    );

                    ong.setDescricao(
                            ongAtualizada.getDescricao()
                    );

                    Ong atualizada =
                            repository.save(ong);

                    OngResponseDTO resposta =
                            new OngResponseDTO(
                                    atualizada.getId(),
                                    atualizada.getNome(),
                                    atualizada.getEmail(),
                                    atualizada.getTelefone(),
                                    atualizada.getCidade(),
                                    atualizada.getDescricao()
                            );

                    return ResponseEntity.ok(
                            resposta
                    );

                }).orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // =========================
    // DELETAR
    // =========================
    public ResponseEntity<?> deletar(
            Long id
    ) {

        return repository.findById(id)
                .map(ong -> {

                    repository.delete(ong);

                    return ResponseEntity
                            .noContent()
                            .build();

                }).orElse(
                        ResponseEntity.notFound().build()
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