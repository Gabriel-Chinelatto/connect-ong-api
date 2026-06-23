package com.example.connectong_api.service;

import com.example.connectong_api.dto.InteresseRequestDTO;
import com.example.connectong_api.dto.InteresseResponseDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InteresseService {

    @Autowired
    private InteresseRepository repository;

    @Autowired
    private NecessidadeRepository necessidadeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // =========================
    // DEMONSTRAR INTERESSE (um doador numa necessidade)
    // =========================
    public ResponseEntity<?> demonstrarInteresse(InteresseRequestDTO dto) {

        if (dto.getNecessidadeId() == null || dto.getDoadorId() == null) {
            return erro("É obrigatório informar necessidadeId e doadorId");
        }

        Necessidade necessidade =
                necessidadeRepository.findById(dto.getNecessidadeId()).orElse(null);
        if (necessidade == null) {
            return erro("Necessidade não encontrada");
        }

        Usuario doador =
                usuarioRepository.findById(dto.getDoadorId()).orElse(null);
        if (doador == null) {
            return erro("Doador não encontrado");
        }

        // evita interesse duplicado do mesmo doador na mesma necessidade
        boolean jaExiste = repository.findByDoadorId(dto.getDoadorId()).stream()
                .anyMatch(i -> i.getNecessidade() != null
                        && i.getNecessidade().getId().equals(dto.getNecessidadeId()));
        if (jaExiste) {
            return erro("Você já demonstrou interesse nesta necessidade");
        }

        Interesse interesse = new Interesse();
        interesse.setNecessidade(necessidade);
        interesse.setDoador(doador);

        Interesse salvo = repository.save(interesse);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toDTO(salvo));
    }

    // =========================
    // LISTAR (por doador, por ONG, ou todos)
    // =========================
    public List<InteresseResponseDTO> listar(Long doadorId, Long ongId) {

        List<Interesse> lista;

        if (doadorId != null) {
            lista = repository.findByDoadorId(doadorId);
        } else if (ongId != null) {
            lista = repository.findByNecessidadeOngId(ongId);
        } else {
            lista = repository.findAll();
        }

        return lista.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // =========================
    // ACEITAR / RECUSAR (a ONG decide)
    // =========================
    public ResponseEntity<?> aceitar(Long id) {
        return mudarStatus(id, "ACEITO");
    }

    public ResponseEntity<?> recusar(Long id) {
        return mudarStatus(id, "RECUSADO");
    }

    private ResponseEntity<?> mudarStatus(Long id, String novoStatus) {
        Interesse interesse = repository.findById(id).orElse(null);
        if (interesse == null) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Interesse não encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erro);
        }
        interesse.setStatus(novoStatus);
        Interesse salvo = repository.save(interesse);
        return ResponseEntity.ok(toDTO(salvo));
    }

    // =========================
    // HELPERS
    // =========================
    private InteresseResponseDTO toDTO(Interesse i) {
        Necessidade n = i.getNecessidade();
        Long ongId = (n != null && n.getOng() != null) ? n.getOng().getId() : null;
        String ongNome = (n != null && n.getOng() != null) ? n.getOng().getNome() : null;

        return new InteresseResponseDTO(
                i.getId(),
                i.getStatus(),
                i.getDataCriacao(),
                n != null ? n.getId() : null,
                n != null ? n.getTitulo() : null,
                i.getDoador() != null ? i.getDoador().getId() : null,
                i.getDoador() != null ? i.getDoador().getNome() : null,
                ongId,
                ongNome
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
