package com.example.connectong_api.service;

import com.example.connectong_api.dto.AvaliacaoRequestDTO;
import com.example.connectong_api.dto.AvaliacaoResponseDTO;
import com.example.connectong_api.model.Avaliacao;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.AvaliacaoRepository;
import com.example.connectong_api.repository.ONGRepository;
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
public class AvaliacaoService {

    @Autowired
    private AvaliacaoRepository repository;

    @Autowired
    private ONGRepository ongRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AtividadeService atividadeService;

    public List<AvaliacaoResponseDTO> listar(Long ongId) {
        return repository.findByOngIdOrderByDataCriacaoDesc(ongId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Cria ou atualiza a avaliacao do doador para a ONG (uma por doador/ONG).
    public ResponseEntity<?> avaliar(AvaliacaoRequestDTO dto) {
        if (dto.getOngId() == null || dto.getDoadorId() == null) {
            return erro("É obrigatório informar ongId e doadorId");
        }
        if (dto.getNota() == null || dto.getNota() < 1 || dto.getNota() > 5) {
            return erro("A nota deve ser de 1 a 5");
        }

        Ong ong = ongRepository.findById(dto.getOngId()).orElse(null);
        if (ong == null) {
            return erro("ONG não encontrada");
        }
        Usuario doador = usuarioRepository.findById(dto.getDoadorId()).orElse(null);
        if (doador == null) {
            return erro("Doador não encontrado");
        }

        Avaliacao avaliacao = repository
                .findByOngIdAndDoadorId(dto.getOngId(), dto.getDoadorId())
                .orElseGet(Avaliacao::new);

        avaliacao.setOngId(dto.getOngId());
        avaliacao.setDoadorId(dto.getDoadorId());
        avaliacao.setDoadorNome(doador.getNome());
        avaliacao.setNota(dto.getNota());
        avaliacao.setComentario(dto.getComentario());

        Avaliacao salva = repository.save(avaliacao);

        recalcularMedia(ong);

        // feed global
        atividadeService.registrar(
                "AVALIACAO",
                ong.getNome() + " recebeu uma nova avaliacao",
                ong.getId(),
                ong.getNome());

        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(salva));
    }

    private void recalcularMedia(Ong ong) {
        List<Avaliacao> todas = repository.findByOngIdOrderByDataCriacaoDesc(ong.getId());
        if (todas.isEmpty()) {
            ong.setNotaMedia(0.0);
            ong.setTotalAvaliacoes(0);
        } else {
            double soma = todas.stream().mapToInt(Avaliacao::getNota).sum();
            double media = Math.round((soma / todas.size()) * 10.0) / 10.0;
            ong.setNotaMedia(media);
            ong.setTotalAvaliacoes(todas.size());
        }
        ongRepository.save(ong);
    }

    private AvaliacaoResponseDTO toDTO(Avaliacao a) {
        return new AvaliacaoResponseDTO(
                a.getId(),
                a.getOngId(),
                a.getDoadorNome(),
                a.getNota(),
                a.getComentario(),
                a.getDataCriacao()
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
