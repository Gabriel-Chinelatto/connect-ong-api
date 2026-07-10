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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coracao do "match": registra o interesse de um doador numa necessidade e
 * gerencia seu ciclo PENDENTE -> ACEITO/RECUSADO -> CONCLUIDO. Impede interesse
 * duplicado do mesmo doador na mesma necessidade; quando a ONG aceita (status
 * ACEITO) forma-se o match que habilita o chat; quando a doacao chega de fato,
 * a ONG marca CONCLUIDO (grava dataConclusao e abre o prazo da prestacao de
 * contas). So a ONG dona da necessidade pode aceitar/recusar/concluir.
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

    @Autowired
    private BloqueioService bloqueioService;

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

        // Re-interesse: bloqueia SOMENTE se ja houver interesse EM ANDAMENTO
        // (PENDENTE ou ACEITO) do mesmo doador nesta necessidade. Interesses
        // anteriores ja CONCLUIDO ou RECUSADO nao bloqueiam — a necessidade
        // recorrente pode ser doada de novo (novo ciclo de match).
        boolean emAndamento = repository.findByDoadorId(dto.getDoadorId()).stream()
                .anyMatch(i -> i.getNecessidade() != null
                        && i.getNecessidade().getId().equals(dto.getNecessidadeId())
                        && ("PENDENTE".equals(i.getStatus())
                                || "ACEITO".equals(i.getStatus())));
        if (emAndamento) {
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

        // BLOQUEIO: matches com ONG bloqueadora continuam listados (historico),
        // mas ganham bloqueadoPelaOng=true para o app desabilitar o chat.
        // Uma unica query por listagem (conjunto do doador ou da ONG).
        java.util.Set<Long> ongsQueBloquearam = doadorId != null
                ? bloqueioService.ongsQueBloquearam(doadorId)
                : java.util.Set.of();
        java.util.Set<Long> doadoresBloqueados = (doadorId == null && ongId != null)
                ? bloqueioService.doadoresBloqueadosPor(ongId)
                : java.util.Set.of();

        return lista.stream()
                .map(i -> {
                    InteresseResponseDTO dto = toDTO(i);
                    boolean bloqueado =
                            (dto.getOngId() != null
                                    && ongsQueBloquearam.contains(dto.getOngId()))
                            || (dto.getDoadorId() != null
                                    && doadoresBloqueados.contains(dto.getDoadorId()));
                    dto.setBloqueadoPelaOng(bloqueado);
                    return dto;
                })
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

    // =========================
    // CONCLUIR (a ONG confirma que a doacao foi recebida)
    // =========================
    // So a ONG dona da necessidade, e so a partir de um match ACEITO. Grava a
    // dataConclusao (abre o prazo de 10 dias da prestacao de contas), notifica
    // o doador e registra no feed global (best-effort).
    public ResponseEntity<?> concluir(Long id) {
        Interesse interesse = repository.findById(id).orElse(null);
        if (interesse == null) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", "Interesse não encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erro);
        }

        // Só a ONG DONA da necessidade pode concluir este match.
        Long ongDonaId = (interesse.getNecessidade() != null
                && interesse.getNecessidade().getOng() != null)
                ? interesse.getNecessidade().getOng().getId()
                : null;
        security.exigirOng(ongDonaId);

        if (!"ACEITO".equals(interesse.getStatus())) {
            return erro("Só um match ACEITO pode ser marcado como concluído");
        }

        LocalDateTime agora = LocalDateTime.now();
        interesse.setStatus("CONCLUIDO");
        interesse.setDataConclusao(agora);
        interesse.setDataStatus(agora);
        Interesse salvo = repository.save(interesse);

        String tituloNec = interesse.getNecessidade() != null
                ? interesse.getNecessidade().getTitulo()
                : "uma necessidade";
        String nomeOng = (interesse.getNecessidade() != null
                && interesse.getNecessidade().getOng() != null)
                ? interesse.getNecessidade().getOng().getNome()
                : "ONG";

        // notifica o doador que a ONG confirmou o recebimento
        if (interesse.getDoador() != null) {
            notificacaoService.criar(
                    interesse.getDoador().getId(),
                    "Doação recebida!",
                    "Sua doação \"" + tituloNec
                            + "\" foi marcada como recebida pela " + nomeOng,
                    "MATCH");
        }

        // feed global best-effort
        atividadeService.registrar(
                "INTERESSE",
                "Doacao \"" + tituloNec + "\" concluida com sucesso",
                ongDonaId,
                nomeOng);

        return ResponseEntity.ok(toDTO(salvo));
    }

    // Aviso ao doador quando a ONG o recusa: antes o doador so via um selo
    // passivo "Recusado". Agora recebe notificacao e sabe que pode tentar de
    // novo (o backend nao bloqueia re-interesse apos RECUSADO).
    private void notificarRecusa(Interesse interesse) {
        if (interesse.getDoador() == null) return;
        String tituloNec = interesse.getNecessidade() != null
                ? interesse.getNecessidade().getTitulo()
                : "uma necessidade";
        notificacaoService.criar(
                interesse.getDoador().getId(),
                "Interesse recusado",
                "A ONG não pôde aceitar seu interesse em \"" + tituloNec
                        + "\" desta vez. Você pode demonstrar interesse novamente"
                        + " ou apoiar outra ONG.",
                "MATCH");
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
        interesse.setDataStatus(LocalDateTime.now());
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

        if ("RECUSADO".equals(novoStatus)) {
            notificarRecusa(interesse);
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

        InteresseResponseDTO dto = new InteresseResponseDTO(
                i.getId(),
                i.getStatus(),
                i.getDataCriacao(),
                i.getDataConclusao(),
                n != null ? n.getId() : null,
                n != null ? n.getTitulo() : null,
                i.getDoador() != null ? i.getDoador().getId() : null,
                i.getDoador() != null ? i.getDoador().getNome() : null,
                ongId,
                ongNome
        );

        // Dias de espera: so faz sentido enquanto PENDENTE (aguardando o aceite).
        if ("PENDENTE".equals(i.getStatus()) && i.getDataCriacao() != null) {
            long dias = ChronoUnit.DAYS.between(i.getDataCriacao(), LocalDateTime.now());
            dto.setDiasEsperando((int) Math.max(0, dias));
        }
        dto.setDataStatus(i.getDataStatus());

        return dto;
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
