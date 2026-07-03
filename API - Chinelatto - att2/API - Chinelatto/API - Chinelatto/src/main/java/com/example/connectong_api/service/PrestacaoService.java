package com.example.connectong_api.service;

import com.example.connectong_api.dto.PendenciaPrestacaoDTO;
import com.example.connectong_api.dto.PrestacaoRequestDTO;
import com.example.connectong_api.dto.PrestacaoResponseDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Prestacao;
import com.example.connectong_api.model.PrestacaoFoto;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.PrestacaoFotoRepository;
import com.example.connectong_api.repository.PrestacaoRepository;
import com.example.connectong_api.security.SecurityUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gerencia a prestacao de contas que a ONG publica dentro de um match.
 * Regras de negocio:
 *  - permitida para um Interesse ACEITO ou CONCLUIDO (a conclusao nao fecha a
 *    porta da prestacao — pelo contrario, abre um prazo de 10 dias);
 *  - pode levar ate 5 fotos (base64) e um valor utilizado (transparencia);
 *  - ao publicar, notifica o doador daquele match e registra no feed global;
 *  - pendencias: matches CONCLUIDOS sem prestacao, com prazo de 10 dias a
 *    partir da dataConclusao (estourou => "definitivo", penaliza o score de
 *    transparencia — ver TransparenciaService). Tudo on-read, sem cron.
 */
@Service
public class PrestacaoService {

    @Autowired
    private PrestacaoRepository repository;

    @Autowired
    private PrestacaoFotoRepository fotoRepository;

    @Autowired
    private InteresseRepository interesseRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private AtividadeService atividadeService;

    @Autowired
    private TransparenciaService transparenciaService;

    @Autowired
    private SecurityUtils security;

    // So os participantes do match (o doador ou a ONG dona) podem ler/publicar.
    private void exigirParticipante(Interesse interesse) {
        Long doadorId = interesse.getDoador() != null
                ? interesse.getDoador().getId() : null;
        Long ongId = (interesse.getNecessidade() != null
                && interesse.getNecessidade().getOng() != null)
                ? interesse.getNecessidade().getOng().getId() : null;
        security.exigirUsuarioOuOng(doadorId, ongId);
    }

    public List<PrestacaoResponseDTO> listar(Long interesseId) {
        Interesse interesse =
                interesseRepository.findById(interesseId).orElse(null);
        if (interesse == null) {
            return List.of(); // match inexistente: nada a listar
        }
        exigirParticipante(interesse);

        List<Prestacao> prestacoes =
                repository.findByInteresseIdOrderByDataCriacaoDesc(interesseId);
        Map<Long, List<String>> fotos = fotosDe(prestacoes);

        return prestacoes.stream()
                .map(p -> toDTO(p, fotos.getOrDefault(p.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    @Transactional
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

        // SEGURANCA: prestacao de contas e um ato da ONG (aparece no perfil publico
        // dela como se ela tivesse prestado contas). So a ONG DONA da necessidade
        // pode publicar — antes usava exigirParticipante, que deixava o DOADOR do
        // match publicar uma prestacao atribuida a ONG. Ler continua liberado aos
        // dois lados (metodo listar usa exigirParticipante).
        Long ongDonaId = (interesse.getNecessidade() != null
                && interesse.getNecessidade().getOng() != null)
                ? interesse.getNecessidade().getOng().getId()
                : null;
        security.exigirOng(ongDonaId);

        // Vale para match ACEITO ou CONCLUIDO (a conclusao ABRE o prazo da
        // prestacao; a publicacao quita a pendencia).
        if (!"ACEITO".equals(interesse.getStatus())
                && !"CONCLUIDO".equals(interesse.getStatus())) {
            return erro("A prestação de contas só vale para um match aceito ou concluído");
        }

        Prestacao p = new Prestacao();
        p.setInteresse(interesse);
        p.setTitulo(dto.getTitulo());
        p.setDescricao(dto.getDescricao());
        p.setFotoUrl(dto.getFotoUrl());
        p.setValorUtilizado(dto.getValorUtilizado());

        Prestacao salva = repository.save(p);

        // Fotos (base64): max 5, cada uma ja limitada a ~2.8MB pelo DTO.
        List<String> fotosSalvas = Collections.emptyList();
        if (dto.getFotos() != null && !dto.getFotos().isEmpty()) {
            for (String foto : dto.getFotos()) {
                if (foto == null || foto.isBlank()) continue;
                PrestacaoFoto pf = new PrestacaoFoto();
                pf.setPrestacaoId(salva.getId());
                pf.setFoto(foto);
                fotoRepository.save(pf);
            }
            fotosSalvas = fotoRepository
                    .findByPrestacaoIdOrderByIdAsc(salva.getId()).stream()
                    .map(PrestacaoFoto::getFoto)
                    .collect(Collectors.toList());
        }

        String tituloNec = interesse.getNecessidade() != null
                ? interesse.getNecessidade().getTitulo()
                : "uma necessidade";
        String nomeOng = (interesse.getNecessidade() != null
                && interesse.getNecessidade().getOng() != null)
                ? interesse.getNecessidade().getOng().getNome()
                : "ONG";

        // notifica o doador
        if (interesse.getDoador() != null) {
            notificacaoService.criar(
                    interesse.getDoador().getId(),
                    "Prestação de contas",
                    "Prestação de contas sobre sua doação \"" + tituloNec
                            + "\" foi publicada pela " + nomeOng
                            + ". Veja o feedback!",
                    "PRESTACAO");
        }

        // feed global
        atividadeService.registrar(
                "PRESTACAO",
                "Nova prestacao de contas: \"" + dto.getTitulo() + "\"",
                ongDonaId,
                nomeOng);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toDTO(salva, fotosSalvas));
    }

    // Prestacoes de uma ONG (perfil publico), ja com fotos e contexto do match.
    // Sem checagem de dono: prestacoes sao o cartao de visita PUBLICO da ONG.
    public List<PrestacaoResponseDTO> listarPorOng(Long ongId) {
        List<Prestacao> prestacoes = repository
                .findByInteresseNecessidadeOngIdOrderByDataCriacaoDesc(ongId);
        Map<Long, List<String>> fotos = fotosDe(prestacoes);
        return prestacoes.stream()
                .map(p -> toDTO(p, fotos.getOrDefault(p.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    // =========================
    // PENDENCIAS (matches CONCLUIDOS sem prestacao) — so a ONG dona ve.
    // =========================
    public ResponseEntity<?> pendencias(Long ongId) {
        security.exigirOng(ongId);

        LocalDateTime agora = LocalDateTime.now();
        List<PendenciaPrestacaoDTO> pendencias = interesseRepository
                .concluidosSemPrestacaoPorOng(ongId).stream()
                .map(i -> {
                    LocalDateTime conclusao = i.getDataConclusao();
                    long decorridos = conclusao != null
                            ? ChronoUnit.DAYS.between(conclusao, agora)
                            : 0;
                    int restantes = (int) Math.max(0,
                            TransparenciaService.PRAZO_PRESTACAO_DIAS - decorridos);
                    boolean definitivo = transparenciaService.ehDefinitiva(conclusao);
                    return new PendenciaPrestacaoDTO(
                            i.getId(),
                            i.getNecessidade() != null ? i.getNecessidade().getTitulo() : null,
                            i.getDoador() != null ? i.getDoador().getNome() : null,
                            conclusao,
                            restantes,
                            definitivo);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(pendencias);
    }

    // Fotos agrupadas por prestacao, carregadas numa unica query (evita N+1).
    private Map<Long, List<String>> fotosDe(List<Prestacao> prestacoes) {
        if (prestacoes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = prestacoes.stream()
                .map(Prestacao::getId)
                .collect(Collectors.toList());
        return fotoRepository.findByPrestacaoIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(
                        PrestacaoFoto::getPrestacaoId,
                        Collectors.mapping(PrestacaoFoto::getFoto,
                                Collectors.toList())));
    }

    private PrestacaoResponseDTO toDTO(Prestacao p, List<String> fotos) {
        Interesse i = p.getInteresse();
        Long doadorId = (i != null && i.getDoador() != null)
                ? i.getDoador().getId() : null;
        String doadorNome = (i != null && i.getDoador() != null)
                ? i.getDoador().getNome() : null;
        String ongNome = (i != null && i.getNecessidade() != null
                && i.getNecessidade().getOng() != null)
                ? i.getNecessidade().getOng().getNome() : null;
        String necessidadeTitulo = (i != null && i.getNecessidade() != null)
                ? i.getNecessidade().getTitulo() : null;

        return new PrestacaoResponseDTO(
                p.getId(),
                i != null ? i.getId() : null,
                p.getTitulo(),
                p.getDescricao(),
                p.getFotoUrl(),
                p.getDataCriacao(),
                doadorId,
                doadorNome,
                ongNome,
                necessidadeTitulo,
                fotos,
                p.getValorUtilizado()
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
