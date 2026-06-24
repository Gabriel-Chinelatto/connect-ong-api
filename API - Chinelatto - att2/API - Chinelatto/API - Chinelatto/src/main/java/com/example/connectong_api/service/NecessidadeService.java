package com.example.connectong_api.service;

import com.example.connectong_api.dto.NecessidadeRequestDTO;
import com.example.connectong_api.dto.NecessidadeResponseDTO;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NecessidadeService {

    @Autowired
    private NecessidadeRepository repository;

    @Autowired
    private ONGRepository ongRepository;

    // =========================
    // LISTAR (filtra por ong ou status, se informados)
    // =========================
    public List<NecessidadeResponseDTO> listar(Long ongId, String status) {

        List<Necessidade> lista;

        if (ongId != null) {
            lista = repository.findByOngId(ongId);
        } else if (status != null && !status.isBlank()) {
            lista = repository.findByStatus(status);
        } else {
            lista = repository.findAll();
        }

        return lista.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================
    // CRIAR (uma ONG publica uma necessidade)
    // =========================
    public ResponseEntity<?> criar(NecessidadeRequestDTO dto) {

        if (dto.getTitulo() == null || dto.getTitulo().isBlank()) {
            return erro("Título da necessidade é obrigatório");
        }

        if (dto.getOngId() == null) {
            return erro("É obrigatório informar a ONG (ongId)");
        }

        Ong ong = ongRepository.findById(dto.getOngId()).orElse(null);
        if (ong == null) {
            return erro("ONG não encontrada");
        }

        Necessidade necessidade = new Necessidade();
        necessidade.setOng(ong);
        necessidade.setTitulo(dto.getTitulo());
        necessidade.setDescricao(dto.getDescricao());
        necessidade.setCategoria(dto.getCategoria());
        necessidade.setUrgente(dto.getUrgente());

        Necessidade salva = repository.save(necessidade);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toDTO(salva));
    }

    // =========================
    // HELPERS
    // =========================
    private NecessidadeResponseDTO toDTO(Necessidade n) {
        return new NecessidadeResponseDTO(
                n.getId(),
                n.getTitulo(),
                n.getDescricao(),
                n.getCategoria(),
                n.getUrgente(),
                n.getStatus(),
                n.getDataCriacao(),
                n.getOng() != null ? n.getOng().getId() : null,
                n.getOng() != null ? n.getOng().getNome() : null,
                n.getOng() != null ? n.getOng().getVerificada() : false,
                n.getOng() != null ? n.getOng().getNotaMedia() : 0.0,
                n.getOng() != null ? n.getOng().getTotalAvaliacoes() : 0
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
