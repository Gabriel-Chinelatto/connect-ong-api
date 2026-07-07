package com.example.connectong_api.service;

import com.example.connectong_api.dto.NecessidadeRequestDTO;
import com.example.connectong_api.dto.NecessidadeResponseDTO;
import com.example.connectong_api.dto.NecessidadeUpdateDTO;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.util.Categorias;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gerencia as necessidades publicadas pelas ONGs (o que cada ONG precisa
 * receber), com listagem filtravel por ONG ou status. Ao criar, registra no
 * feed global e notifica os doadores que favoritaram aquela ONG; essa
 * notificacao de seguidores e best-effort (falha nela nao impede a publicacao).
 */
@Service
public class NecessidadeService {

    @Autowired
    private NecessidadeRepository repository;

    @Autowired
    private ONGRepository ongRepository;

    @Autowired
    private AtividadeService atividadeService;

    @Autowired
    private com.example.connectong_api.repository.FavoritoRepository favoritoRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private SecurityUtils security;

    @Autowired
    private BloqueioService bloqueioService;

    // =========================
    // LISTAR (filtra por ong, status ou categoria, se informados)
    // =========================
    public List<NecessidadeResponseDTO> listar(Long ongId, String status, String categoria) {

        List<Necessidade> lista;

        if (ongId != null) {
            lista = repository.findByOngId(ongId);
        } else if (status != null && !status.isBlank()) {
            lista = repository.findByStatus(status);
        } else {
            lista = repository.findAll();
        }

        // BLOQUEIO: para um DOADOR autenticado, as ONGs que o bloquearam somem
        // do feed (uma unica query; requisicao anonima/ONG = conjunto vazio).
        java.util.Set<Long> ongsBloqueadoras =
                bloqueioService.ongIdsQueBloquearamDoadorAtual();

        return lista.stream()
                // esconde necessidades de ONGs excluidas (soft-delete)
                .filter(n -> n.getOng() == null || n.getOng().getDataExclusao() == null)
                // esconde necessidades de ONGs que bloquearam o doador
                .filter(n -> n.getOng() == null
                        || !ongsBloqueadoras.contains(n.getOng().getId()))
                // filtro por categoria em memoria (compara valores normalizados)
                .filter(n -> categoria == null || categoria.isBlank()
                        || Categorias.iguais(n.getCategoria(), categoria))
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

        // So a propria ONG dona pode publicar necessidades em seu nome (senao 403).
        security.exigirOng(dto.getOngId());

        Ong ong = ongRepository.findById(dto.getOngId()).orElse(null);
        if (ong == null) {
            return erro("ONG não encontrada");
        }

        Necessidade necessidade = new Necessidade();
        necessidade.setOng(ong);
        necessidade.setTitulo(dto.getTitulo());
        necessidade.setDescricao(dto.getDescricao());
        necessidade.setCategoria(Categorias.normalizar(dto.getCategoria()));
        necessidade.setUrgente(dto.getUrgente());

        Necessidade salva = repository.save(necessidade);

        atividadeService.registrar(
                "NECESSIDADE",
                ong.getNome() + " publicou uma nova necessidade: \""
                        + salva.getTitulo() + "\"",
                ong.getId(),
                ong.getNome());

        // notifica os doadores que favoritaram esta ONG (best-effort)
        try {
            favoritoRepository.findByTipoAndAlvoId("ONG", ong.getId())
                    .forEach(fav -> notificacaoService.criar(
                            fav.getUsuarioId(),
                            "ONG que voce segue",
                            ong.getNome() + " publicou: \"" + salva.getTitulo() + "\"",
                            "FAVORITO"));
        } catch (Exception ignorado) {
            // notificacao de seguidores e best-effort
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toDTO(salva));
    }

    // =========================
    // EDITAR (somente a ONG dona)
    // =========================
    public ResponseEntity<?> atualizar(Long id, NecessidadeUpdateDTO dto) {

        Necessidade necessidade = repository.findById(id).orElse(null);
        if (necessidade == null) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Necessidade não encontrada");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erro);
        }

        // So a ONG DONA da necessidade pode edita-la (senao 403). O dono vem da
        // propria necessidade, nunca do cliente.
        Long ongDonaId = necessidade.getOng() != null
                ? necessidade.getOng().getId() : null;
        security.exigirOng(ongDonaId);

        necessidade.setTitulo(dto.getTitulo());
        necessidade.setDescricao(dto.getDescricao());
        necessidade.setCategoria(Categorias.normalizar(dto.getCategoria()));
        necessidade.setUrgente(dto.getUrgente());

        return ResponseEntity.ok(toDTO(repository.save(necessidade)));
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
                n.getOng() != null ? n.getOng().getCidade() : null,
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
