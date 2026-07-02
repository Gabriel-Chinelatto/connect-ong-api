package com.example.connectong_api.service;

import com.example.connectong_api.dto.ChatStatusDTO;
import com.example.connectong_api.dto.MensagemRequestDTO;
import com.example.connectong_api.dto.MensagemResponseDTO;
import com.example.connectong_api.dto.ReacaoDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Mensagem;
import com.example.connectong_api.model.Reacao;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.MensagemRepository;
import com.example.connectong_api.repository.ReacaoRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.security.SecurityUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gerencia o chat de um match (mensagens vinculadas a um Interesse).
 * Regra de negocio: so e possivel enviar mensagem apos o match estar ACEITO, e
 * apenas os dois participantes (o doador e a ONG dona da necessidade) podem ler
 * ou enviar, senao lanca AcessoNegadoException (403). Cada envio notifica o outro lado.
 */
@Service
public class MensagemService {

    @Autowired
    private MensagemRepository repository;

    @Autowired
    private InteresseRepository interesseRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private SecurityUtils security;

    @Autowired
    private DigitandoRegistry digitando;

    @Autowired
    private ReacaoRepository reacaoRepository;

    // Codigos de reacao aceitos (o emoji em si e renderizado no app; guardar o
    // emoji cru quebraria no utf8 de 3 bytes do MySQL 5.6).
    private static final Set<String> EMOJIS_PERMITIDOS =
            Set.of("LIKE", "LOVE", "LAUGH", "WOW", "SAD", "PRAY");

    // "Online" = teve atividade no chat nos ultimos 45s (o poll e de ~2s, entao
    // isso cobre folgadamente uma perda de batimento). "Digitando" = heartbeat nos
    // ultimos 5s.
    private static final Duration JANELA_ONLINE = Duration.ofSeconds(45);
    private static final Duration JANELA_DIGITANDO = Duration.ofSeconds(5);

    // Lista as mensagens de um match, em ordem cronologica, e marca como lidas as
    // mensagens que o OUTRO lado enviou (o participante abriu/atualizou o chat ->
    // "visto"). So os participantes do match (o doador ou a ONG) podem ler.
    @Transactional
    public List<MensagemResponseDTO> listar(Long interesseId) {
        Interesse interesse =
                interesseRepository.findById(interesseId).orElse(null);
        if (interesse == null) {
            return List.of(); // match inexistente: nada a listar
        }
        exigirParticipante(interesse);

        // Marca "visto" nas mensagens do outro lado (as minhas nao mudam aqui).
        String outroLado = ladoOposto(ladoDoChamador(interesse));
        repository.marcarLidas(interesseId, outroLado, LocalDateTime.now());

        List<Mensagem> mensagens =
                repository.findByInteresseIdOrderByDataEnvioAsc(interesseId);

        // Carrega as reacoes de todas as mensagens em UMA query e agrupa por
        // mensagem (evita N+1 no chat).
        Map<Long, List<ReacaoDTO>> reacoesPorMensagem = reacoesDe(mensagens);

        return mensagens.stream()
                .map(m -> toDTO(m, reacoesPorMensagem.getOrDefault(
                        m.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
    }

    // Reacoes agrupadas por mensagem, carregadas em lote.
    private Map<Long, List<ReacaoDTO>> reacoesDe(List<Mensagem> mensagens) {
        if (mensagens.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = mensagens.stream().map(Mensagem::getId).collect(Collectors.toList());
        return reacaoRepository.findByMensagemIdIn(ids).stream()
                .collect(Collectors.groupingBy(
                        Reacao::getMensagemId,
                        Collectors.mapping(
                                r -> new ReacaoDTO(r.getEmoji(), r.getLado()),
                                Collectors.toList())));
    }

    // Situacao do OUTRO participante (online / visto por ultimo / digitando) e,
    // de quebra, registra o batimento de presenca de quem chamou (o poll do chat).
    @Transactional
    public ChatStatusDTO status(Long interesseId) {
        Interesse interesse =
                interesseRepository.findById(interesseId).orElse(null);
        if (interesse == null) {
            return new ChatStatusDTO(false, null, false);
        }
        exigirParticipante(interesse);

        // Batimento: marca a MINHA ultima atividade (presenca).
        Long meuId = security.usuarioId();
        if (meuId != null) {
            usuarioRepository.marcarVisto(meuId, LocalDateTime.now());
        }

        String meuLado = ladoDoChamador(interesse);
        String outroLado = ladoOposto(meuLado);

        // ultimo_visto do OUTRO participante.
        Usuario outro = participante(interesse, outroLado);
        LocalDateTime ultimoVisto = outro != null ? outro.getUltimoVisto() : null;
        boolean online = ultimoVisto != null
                && ultimoVisto.isAfter(LocalDateTime.now().minus(JANELA_ONLINE));

        boolean estaDigitando =
                digitando.estaDigitando(interesseId, outroLado, JANELA_DIGITANDO);

        return new ChatStatusDTO(online, ultimoVisto, estaDigitando);
    }

    // Registra que quem chamou esta digitando agora (heartbeat efemero em memoria).
    public void registrarDigitando(Long interesseId) {
        Interesse interesse =
                interesseRepository.findById(interesseId).orElse(null);
        if (interesse == null) {
            return;
        }
        exigirParticipante(interesse);
        digitando.marcar(interesseId, ladoDoChamador(interesse));
    }

    // Garante que o usuario autenticado e o doador do match OU a ONG do match.
    private void exigirParticipante(Interesse interesse) {
        Long doadorId = interesse.getDoador() != null
                ? interesse.getDoador().getId() : null;
        Long ongId = (interesse.getNecessidade() != null
                && interesse.getNecessidade().getOng() != null)
                ? interesse.getNecessidade().getOng().getId() : null;
        security.exigirUsuarioOuOng(doadorId, ongId);
    }

    // Qual lado (DOADOR/ONG) e o usuario autenticado. So faz sentido apos
    // exigirParticipante ter garantido que ele participa do match.
    private String ladoDoChamador(Interesse interesse) {
        Long doadorId = interesse.getDoador() != null
                ? interesse.getDoador().getId() : null;
        return (doadorId != null && doadorId.equals(security.usuarioId()))
                ? "DOADOR" : "ONG";
    }

    private String ladoOposto(String lado) {
        return "DOADOR".equals(lado) ? "ONG" : "DOADOR";
    }

    // A conta de Usuario de um dos lados do match (para ler o ultimo_visto).
    private Usuario participante(Interesse interesse, String lado) {
        if ("DOADOR".equals(lado)) {
            return interesse.getDoador() != null
                    ? usuarioRepository.findById(interesse.getDoador().getId()).orElse(null)
                    : null;
        }
        Long ongId = (interesse.getNecessidade() != null
                && interesse.getNecessidade().getOng() != null)
                ? interesse.getNecessidade().getOng().getId() : null;
        return ongId != null
                ? usuarioRepository.findByOngId(ongId).orElse(null)
                : null;
    }

    // Envia uma mensagem (so apos o match ser aceito).
    // @Transactional: o save da mensagem e a notificacao ao outro lado ficam
    // numa unica transacao (ou os dois acontecem, ou nenhum).
    @Transactional
    public ResponseEntity<?> enviar(MensagemRequestDTO dto) {

        if (dto.getInteresseId() == null) {
            return erro("É obrigatório informar o interesseId (o match)");
        }

        if (dto.getConteudo() == null || dto.getConteudo().isBlank()) {
            return erro("A mensagem não pode ser vazia");
        }

        Interesse interesse =
                interesseRepository.findById(dto.getInteresseId()).orElse(null);
        if (interesse == null) {
            return erro("Match não encontrado");
        }

        // So os participantes do match podem enviar mensagem.
        exigirParticipante(interesse);

        // SEGURANCA: o remetente e derivado da IDENTIDADE DO TOKEN, nunca do corpo.
        // Antes vinha do cliente, entao o doador podia enviar {"remetente":"ONG"} e
        // a mensagem aparecia como se fosse da ONG (spoofing). Como exigirParticipante
        // ja garantiu que o usuario e o doador OU a ONG do match, basta ver qual lado.
        Long doadorId = interesse.getDoador() != null
                ? interesse.getDoador().getId() : null;
        final String remetente =
                (doadorId != null && doadorId.equals(security.usuarioId()))
                        ? "DOADOR" : "ONG";

        if (!"ACEITO".equals(interesse.getStatus())) {
            return erro("O chat só fica disponível após o match ser aceito");
        }

        Mensagem mensagem = new Mensagem();
        mensagem.setInteresse(interesse);
        mensagem.setRemetente(remetente);
        mensagem.setConteudo(dto.getConteudo());

        Mensagem salva = repository.save(mensagem);

        // notifica o outro lado da conversa
        if (remetente.equals("DOADOR")) {
            // notifica a ONG
            if (interesse.getNecessidade() != null
                    && interesse.getNecessidade().getOng() != null) {
                usuarioRepository
                        .findByOngId(interesse.getNecessidade().getOng().getId())
                        .ifPresent(ongUser -> notificacaoService.criar(
                                ongUser.getId(),
                                "Nova mensagem",
                                "Você recebeu uma nova mensagem de um doador.",
                                "MENSAGEM"));
            }
        } else {
            // remetente ONG -> notifica o doador
            if (interesse.getDoador() != null) {
                notificacaoService.criar(
                        interesse.getDoador().getId(),
                        "Nova mensagem",
                        "A ONG enviou uma nova mensagem.",
                        "MENSAGEM");
            }
        }

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toDTO(salva, Collections.emptyList())); // mensagem nova: sem reacoes
    }

    // Reage (emoji) a uma mensagem, ou remove a propria reacao (toggle). No maximo
    // 1 reacao por participante por mensagem. So participantes do match reagem.
    @Transactional
    public ResponseEntity<?> reagir(Long mensagemId, String emoji) {
        if (emoji == null || !EMOJIS_PERMITIDOS.contains(emoji)) {
            return erro("Reacao invalida. Use uma das: " + EMOJIS_PERMITIDOS);
        }

        Mensagem mensagem = repository.findById(mensagemId).orElse(null);
        if (mensagem == null || mensagem.getInteresse() == null) {
            return erro("Mensagem nao encontrada");
        }

        Interesse interesse = mensagem.getInteresse();
        exigirParticipante(interesse);

        Long meuId = security.usuarioId();
        String meuLado = ladoDoChamador(interesse);

        Reacao existente = reacaoRepository
                .findByMensagemIdAndUsuarioId(mensagemId, meuId).orElse(null);

        if (existente != null && existente.getEmoji().equals(emoji)) {
            // Mesma reacao de novo = desfaz (toggle off).
            reacaoRepository.delete(existente);
        } else if (existente != null) {
            // Troca a reacao anterior por outra.
            existente.setEmoji(emoji);
            reacaoRepository.save(existente);
        } else {
            Reacao nova = new Reacao();
            nova.setMensagemId(mensagemId);
            nova.setUsuarioId(meuId);
            nova.setLado(meuLado);
            nova.setEmoji(emoji);
            reacaoRepository.save(nova);
        }

        // Devolve as reacoes atuais da mensagem (0..2).
        List<ReacaoDTO> reacoes = reacaoRepository.findByMensagemId(mensagemId).stream()
                .map(r -> new ReacaoDTO(r.getEmoji(), r.getLado()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(reacoes);
    }

    private MensagemResponseDTO toDTO(Mensagem m, List<ReacaoDTO> reacoes) {
        return new MensagemResponseDTO(
                m.getId(),
                m.getInteresse() != null ? m.getInteresse().getId() : null,
                m.getRemetente(),
                m.getConteudo(),
                m.getDataEnvio(),
                m.getDataLeitura() != null, // lida = ja tem recibo de leitura
                reacoes
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
