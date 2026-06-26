package com.example.connectong_api.service;

import com.example.connectong_api.dto.InteresseRequestDTO;
import com.example.connectong_api.dto.InteresseResponseDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.security.SecurityUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coracao do "match": registra o interesse de um doador numa necessidade e
 * gerencia seu ciclo PENDENTE -> ACEITO/RECUSADO. Impede interesse duplicado do
 * mesmo doador na mesma necessidade; quando a ONG aceita (status ACEITO) forma-se
 * o match que habilita o chat. So a ONG dona da necessidade pode aceitar/recusar.
 */
@Service
public class InteresseService {

    @Autowired
    private InteresseRepository repository;

    @Autowired
    private NecessidadeRepository necessidadeRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private AtividadeService atividadeService;

    @Autowired
    private SecurityUtils security;

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

        // notifica a ONG dona da necessidade
        if (necessidade.getOng() != null) {
            usuarioRepository.findByOngId(necessidade.getOng().getId())
                    .ifPresent(ongUser -> notificacaoService.criar(
                            ongUser.getId(),
                            "Novo interesse!",
                            doador.getNome() + " demonstrou interesse em \""
                                    + necessidade.getTitulo() + "\"",
                            "MATCH"));
        }

        // feed global (doador anonimo: feed e publico)
        Long ongId = necessidade.getOng() != null ? necessidade.getOng().getId() : null;
        String ongNome = necessidade.getOng() != null ? necessidade.getOng().getNome() : null;
        atividadeService.registrar(
                "INTERESSE",
                "Alguem demonstrou interesse em \"" + necessidade.getTitulo() + "\"",
                ongId,
                ongNome);

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

        // Só a ONG DONA da necessidade pode aceitar/recusar este interesse.
        Long ongDonaId = (interesse.getNecessidade() != null
                && interesse.getNecessidade().getOng() != null)
                ? interesse.getNecessidade().getOng().getId()
                : null;
        security.exigirOng(ongDonaId);

        interesse.setStatus(novoStatus);
        Interesse salvo = repository.save(interesse);

        // notifica o doador quando o match e aceito
        if ("ACEITO".equals(novoStatus) && interesse.getDoador() != null) {
            String tituloNec = interesse.getNecessidade() != null
                    ? interesse.getNecessidade().getTitulo()
                    : "uma necessidade";
            notificacaoService.criar(
                    interesse.getDoador().getId(),
                    "Interesse aceito!",
                    "A ONG aceitou seu interesse em \"" + tituloNec
                            + "\". Agora vocês podem conversar!",
                    "MATCH");

            // feed global: novo match formado
            Necessidade nec = interesse.getNecessidade();
            Long ongId = (nec != null && nec.getOng() != null) ? nec.getOng().getId() : null;
            String ongNome = (nec != null && nec.getOng() != null) ? nec.getOng().getNome() : null;
            atividadeService.registrar(
                    "INTERESSE",
                    "Novo match formado em \"" + tituloNec + "\"",
                    ongId,
                    ongNome);
        }

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
