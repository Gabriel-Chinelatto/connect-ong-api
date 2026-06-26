package com.example.connectong_api.service;

import com.example.connectong_api.dto.CampanhaRequestDTO;
import com.example.connectong_api.dto.CampanhaResponseDTO;
import com.example.connectong_api.model.Campanha;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.CampanhaRepository;
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
public class CampanhaService {

    @Autowired private CampanhaRepository repository;
    @Autowired private ONGRepository ongRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NotificacaoService notificacaoService;
    @Autowired private AtividadeService atividadeService;

    // =========================
    // LISTAR
    // =========================
    public List<CampanhaResponseDTO> listar(Long ongId, boolean somenteAbertas) {
        List<Campanha> lista;
        if (ongId != null) {
            lista = repository.findByOngIdOrderByIdDesc(ongId);
        } else if (somenteAbertas) {
            lista = repository.findByEncerradaFalseOrderByIdDesc();
        } else {
            lista = repository.findAll();
        }
        return lista.stream()
                .filter(c -> c.getOng() != null) // ignora campanhas orfas (dados legados)
                .map(CampanhaResponseDTO::new)
                .collect(Collectors.toList());
    }

    public List<CampanhaResponseDTO> destaques() {
        return repository.findByDestaqueTrueAndEncerradaFalseOrderByIdDesc()
                .stream()
                .filter(c -> c.getOng() != null)
                .map(CampanhaResponseDTO::new)
                .collect(Collectors.toList());
    }

    // =========================
    // CRIAR
    // =========================
    public ResponseEntity<?> criar(CampanhaRequestDTO dto) {
        Ong ong = ongRepository.findById(dto.getOngId()).orElse(null);
        if (ong == null) return erro("ONG nao encontrada");

        Campanha c = new Campanha();
        c.setTitulo(dto.getTitulo());
        c.setDescricao(dto.getDescricao());
        c.setMetaValor(dto.getMetaValor());
        c.setCategoria(dto.getCategoria());
        c.setDataInicio(dto.getDataInicio());
        c.setDataFim(dto.getDataFim());
        c.setDestaque(dto.getDestaque() != null && dto.getDestaque());
        c.setOng(ong);

        Campanha salva = repository.save(c);

        atividadeService.registrar(
                "CAMPANHA",
                ong.getNome() + " lancou a campanha \"" + salva.getTitulo() + "\"",
                ong.getId(),
                ong.getNome());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CampanhaResponseDTO(salva));
    }

    // =========================
    // CONTRIBUIR (incrementa o arrecadado; encerra ao bater a meta)
    // =========================
    public ResponseEntity<?> contribuir(Long id, Double valor, String doadorNome) {
        if (valor == null || valor <= 0) return erro("O valor deve ser maior que zero");

        Campanha c = repository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        if (c.getEncerrada()) return erro("Esta campanha ja foi encerrada");

        c.setValorArrecadado(c.getValorArrecadado() + valor);
        boolean atingiuMeta = c.getMetaValor() != null
                && c.getValorArrecadado() >= c.getMetaValor();
        if (atingiuMeta) c.setEncerrada(true);

        Campanha salva = repository.save(c);

        // notifica a conta da ONG dona da campanha
        if (c.getOng() != null) {
            usuarioRepository.findByOngId(c.getOng().getId()).ifPresent(ongUser -> {
                String quem = (doadorNome != null && !doadorNome.isBlank())
                        ? doadorNome : "Alguem";
                String msg = atingiuMeta
                        ? "A campanha \"" + c.getTitulo() + "\" atingiu a meta!"
                        : quem + " contribuiu com R$ " + String.format("%.2f", valor)
                                + " na campanha \"" + c.getTitulo() + "\".";
                notificacaoService.criar(ongUser.getId(), "Campanha", msg, "CAMPANHA");
            });
        }

        // feed global: so registra o marco de meta atingida (evita ruido)
        if (atingiuMeta && c.getOng() != null) {
            atividadeService.registrar(
                    "CAMPANHA",
                    "A campanha \"" + c.getTitulo() + "\" atingiu a meta!",
                    c.getOng().getId(),
                    c.getOng().getNome());
        }

        return ResponseEntity.ok(new CampanhaResponseDTO(salva));
    }

    // =========================
    // ENCERRAR
    // =========================
    public ResponseEntity<?> encerrar(Long id) {
        Campanha c = repository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        c.setEncerrada(true);
        return ResponseEntity.ok(new CampanhaResponseDTO(repository.save(c)));
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
