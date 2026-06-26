package com.example.connectong_api.service;

import com.example.connectong_api.dto.FavoritoResponseDTO;
import com.example.connectong_api.model.Favorito;
import com.example.connectong_api.repository.CampanhaRepository;
import com.example.connectong_api.repository.FavoritoRepository;
import com.example.connectong_api.repository.ONGRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gerencia os favoritos do doador, que podem apontar para uma ONG ou uma
 * CAMPANHA (tipo normalizado para maiusculas e validado). Adicionar e
 * idempotente: nao duplica um favorito ja existente. Ao listar, resolve o nome
 * atual do alvo, exibindo "removida" quando o alvo nao existe mais.
 */
@Service
public class FavoritoService {

    @Autowired private FavoritoRepository repository;
    @Autowired private ONGRepository ongRepository;
    @Autowired private CampanhaRepository campanhaRepository;

    public List<FavoritoResponseDTO> listar(Long usuarioId) {
        return repository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId)
                .stream()
                .map(f -> new FavoritoResponseDTO(f, resolverNome(f.getTipo(), f.getAlvoId())))
                .collect(Collectors.toList());
    }

    // Lista os ids dos alvos favoritados de um tipo (para o app marcar o coracao).
    public List<Long> ids(Long usuarioId, String tipo) {
        return repository.findByUsuarioIdAndTipo(usuarioId, normalizar(tipo))
                .stream().map(Favorito::getAlvoId).collect(Collectors.toList());
    }

    public ResponseEntity<?> adicionar(Long usuarioId, String tipo, Long alvoId) {
        if (usuarioId == null || alvoId == null || tipo == null) {
            return erro("usuarioId, tipo e alvoId sao obrigatorios");
        }
        String t = normalizar(tipo);
        if (!t.equals("ONG") && !t.equals("CAMPANHA")) {
            return erro("tipo deve ser ONG ou CAMPANHA");
        }
        if (repository.findByUsuarioIdAndTipoAndAlvoId(usuarioId, t, alvoId).isEmpty()) {
            Favorito f = new Favorito();
            f.setUsuarioId(usuarioId);
            f.setTipo(t);
            f.setAlvoId(alvoId);
            repository.save(f);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("favorito", true));
    }

    public ResponseEntity<?> remover(Long usuarioId, String tipo, Long alvoId) {
        String t = normalizar(tipo);
        repository.findByUsuarioIdAndTipoAndAlvoId(usuarioId, t, alvoId)
                .ifPresent(repository::delete);
        return ResponseEntity.ok(Map.of("favorito", false));
    }

    private String resolverNome(String tipo, Long alvoId) {
        if ("CAMPANHA".equals(tipo)) {
            return campanhaRepository.findById(alvoId)
                    .map(c -> c.getTitulo()).orElse("Campanha removida");
        }
        return ongRepository.findById(alvoId)
                .map(o -> o.getNome()).orElse("ONG removida");
    }

    private String normalizar(String tipo) {
        return tipo == null ? "" : tipo.trim().toUpperCase();
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
