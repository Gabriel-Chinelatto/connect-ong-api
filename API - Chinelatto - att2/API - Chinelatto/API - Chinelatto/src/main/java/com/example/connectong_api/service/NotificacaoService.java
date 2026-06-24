package com.example.connectong_api.service;

import com.example.connectong_api.dto.NotificacaoResponseDTO;
import com.example.connectong_api.model.Notificacao;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.repository.NotificacaoRepository;
import com.example.connectong_api.repository.PreferenciaRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificacaoService {

    @Autowired
    private NotificacaoRepository repository;

    @Autowired
    private PreferenciaRepository preferenciaRepository;

    // Cria uma notificacao, respeitando as preferencias do destinatario.
    public void criar(Long usuarioId, String titulo, String mensagem, String tipo) {
        if (usuarioId == null) return;
        if (!permitido(usuarioId, tipo)) return;

        Notificacao n = new Notificacao();
        n.setUsuarioId(usuarioId);
        n.setTitulo(titulo);
        n.setMensagem(mensagem);
        n.setTipo(tipo);
        repository.save(n);
    }

    private boolean permitido(Long usuarioId, String tipo) {
        Preferencia p = preferenciaRepository.findByUsuarioId(usuarioId).orElse(null);
        if (p == null) return true; // sem preferencia salva -> permite
        return switch (tipo) {
            case "MATCH" -> Boolean.TRUE.equals(p.getNotifMatch());
            case "MENSAGEM" -> Boolean.TRUE.equals(p.getNotifMensagens());
            case "PRESTACAO" -> Boolean.TRUE.equals(p.getNotifCampanhas());
            case "NECESSIDADE" -> Boolean.TRUE.equals(p.getNotifNecessidades());
            default -> true;
        };
    }

    public List<NotificacaoResponseDTO> listar(Long usuarioId) {
        return repository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ResponseEntity<?> contarNaoLidas(Long usuarioId) {
        Map<String, Object> body = new HashMap<>();
        body.put("naoLidas", repository.countByUsuarioIdAndLidaFalse(usuarioId));
        return ResponseEntity.ok(body);
    }

    public ResponseEntity<?> marcarLida(Long id) {
        return repository.findById(id)
                .map(n -> {
                    n.setLida(true);
                    repository.save(n);
                    return ResponseEntity.ok(toDTO(n));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    public ResponseEntity<?> marcarTodas(Long usuarioId) {
        List<Notificacao> lista =
                repository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId);
        for (Notificacao n : lista) {
            n.setLida(true);
        }
        repository.saveAll(lista);
        Map<String, String> ok = new HashMap<>();
        ok.put("mensagem", "Todas marcadas como lidas");
        return ResponseEntity.ok(ok);
    }

    private NotificacaoResponseDTO toDTO(Notificacao n) {
        return new NotificacaoResponseDTO(
                n.getId(),
                n.getTitulo(),
                n.getMensagem(),
                n.getTipo(),
                n.getLida(),
                n.getDataCriacao()
        );
    }
}
