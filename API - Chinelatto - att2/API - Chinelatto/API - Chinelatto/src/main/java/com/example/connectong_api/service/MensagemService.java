package com.example.connectong_api.service;

import com.example.connectong_api.dto.MensagemRequestDTO;
import com.example.connectong_api.dto.MensagemResponseDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Mensagem;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.MensagemRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.security.SecurityUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // Lista as mensagens de um match, em ordem cronologica.
    // So os participantes do match (o doador ou a ONG) podem ler.
    public List<MensagemResponseDTO> listar(Long interesseId) {
        Interesse interesse =
                interesseRepository.findById(interesseId).orElse(null);
        if (interesse == null) {
            return List.of(); // match inexistente: nada a listar
        }
        exigirParticipante(interesse);

        return repository.findByInteresseIdOrderByDataEnvioAsc(interesseId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
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
                .body(toDTO(salva));
    }

    private MensagemResponseDTO toDTO(Mensagem m) {
        return new MensagemResponseDTO(
                m.getId(),
                m.getInteresse() != null ? m.getInteresse().getId() : null,
                m.getRemetente(),
                m.getConteudo(),
                m.getDataEnvio()
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
