package com.example.connectong_api.service;

import com.example.connectong_api.dto.PrestacaoRequestDTO;
import com.example.connectong_api.dto.PrestacaoResponseDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Prestacao;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.PrestacaoRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PrestacaoService {

    @Autowired
    private PrestacaoRepository repository;

    @Autowired
    private InteresseRepository interesseRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    public List<PrestacaoResponseDTO> listar(Long interesseId) {
        return repository.findByInteresseIdOrderByDataCriacaoDesc(interesseId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ResponseEntity<?> criar(PrestacaoRequestDTO dto) {
        if (dto.getInteresseId() == null) {
            return erro("É obrigatório informar o interesseId (o match)");
        }
        if (dto.getTitulo() == null || dto.getTitulo().isBlank()) {
            return erro("O título é obrigatório");
        }

        Interesse interesse =
                interesseRepository.findById(dto.getInteresseId()).orElse(null);
        if (interesse == null) {
            return erro("Match não encontrado");
        }
        if (!"ACEITO".equals(interesse.getStatus())) {
            return erro("A prestação de contas só vale para um match aceito");
        }

        Prestacao p = new Prestacao();
        p.setInteresse(interesse);
        p.setTitulo(dto.getTitulo());
        p.setDescricao(dto.getDescricao());
        p.setFotoUrl(dto.getFotoUrl());

        Prestacao salva = repository.save(p);

        // notifica o doador
        if (interesse.getDoador() != null) {
            notificacaoService.criar(
                    interesse.getDoador().getId(),
                    "Prestação de contas",
                    "A ONG publicou: \"" + dto.getTitulo() + "\"",
                    "PRESTACAO");
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toDTO(salva));
    }

    private PrestacaoResponseDTO toDTO(Prestacao p) {
        return new PrestacaoResponseDTO(
                p.getId(),
                p.getInteresse() != null ? p.getInteresse().getId() : null,
                p.getTitulo(),
                p.getDescricao(),
                p.getFotoUrl(),
                p.getDataCriacao()
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
