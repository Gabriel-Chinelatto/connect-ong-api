package com.example.connectong_api.service;

import com.example.connectong_api.dto.BloqueioResponseDTO;
import com.example.connectong_api.exception.AcessoNegadoException;
import com.example.connectong_api.model.Bloqueio;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.BloqueioRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.security.SecurityUtils;
import com.example.connectong_api.security.UsuarioAutenticado;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bloqueio de doadores pela ONG. So a ONG autenticada gerencia OS PROPRIOS
 * bloqueios (o ongId vem sempre do token, nunca do cliente). Bloquear e
 * desbloquear sao IDEMPOTENTES: repetir a operacao devolve 200 com a mesma
 * mensagem, sem duplicar linha nem dar erro.
 *
 * O efeito do bloqueio ("a ONG some para o doador") e aplicado nos services de
 * feed (NecessidadeService/CampanhaService/ONGService), no chat (MensagemService)
 * e no perfil publico (ONGService.perfilPublico) atraves dos helpers de leitura
 * daqui (bloqueado / ongIdsQueBloquearamDoadorAtual / doadoresBloqueadosPor).
 */
@Service
public class BloqueioService {

    @Autowired
    private BloqueioRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private SecurityUtils security;

    // ===================== ESCRITA (so a propria ONG) =====================

    @Transactional
    public ResponseEntity<?> bloquear(Long doadorId) {
        Long ongId = exigirOngAutenticada();

        if (doadorId == null) {
            return erro("É obrigatório informar o doadorId");
        }

        Usuario doador = usuarioRepository.findById(doadorId).orElse(null);
        if (doador == null || !"DOADOR".equals(doador.getTipo())) {
            return erro("Doador não encontrado");
        }

        // Idempotente: se o par ja existe, nao duplica nem falha.
        if (!repository.existsByOngIdAndDoadorId(ongId, doadorId)) {
            repository.save(new Bloqueio(ongId, doadorId));
            // Auditoria SEM dados sensiveis (so ids; nada de nome/email).
            auditService.registrar("DOADOR_BLOQUEADO", security.usuarioId(),
                    "ONG id=" + ongId + " bloqueou o doador id=" + doadorId);
        }

        return mensagem("Doador bloqueado.");
    }

    @Transactional
    public ResponseEntity<?> desbloquear(Long doadorId) {
        Long ongId = exigirOngAutenticada();

        repository.findByOngIdAndDoadorId(ongId, doadorId).ifPresent(b -> {
            repository.delete(b);
            auditService.registrar("DOADOR_DESBLOQUEADO", security.usuarioId(),
                    "ONG id=" + ongId + " desbloqueou o doador id=" + doadorId);
        });

        // Idempotente: desbloquear quem nao estava bloqueado tambem devolve 200.
        return mensagem("Doador desbloqueado.");
    }

    // ===================== LEITURA =====================

    /** Lista os bloqueios DA PROPRIA ONG autenticada (com o nome do doador). */
    public ResponseEntity<?> listar() {
        Long ongId = exigirOngAutenticada();

        List<BloqueioResponseDTO> lista = repository
                .findByOngIdOrderByCriadoEmDesc(ongId).stream()
                .map(b -> new BloqueioResponseDTO(
                        b.getDoadorId(),
                        usuarioRepository.findById(b.getDoadorId())
                                .map(Usuario::getNome).orElse(null),
                        b.getCriadoEm()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(lista);
    }

    /** O par (ong, doador) esta bloqueado? */
    public boolean bloqueado(Long ongId, Long doadorId) {
        if (ongId == null || doadorId == null) return false;
        return repository.existsByOngIdAndDoadorId(ongId, doadorId);
    }

    /** ONGs que bloquearam um doador (para excluir do feed/busca dele). */
    public Set<Long> ongsQueBloquearam(Long doadorId) {
        if (doadorId == null) return Set.of();
        return repository.findByDoadorId(doadorId).stream()
                .map(Bloqueio::getOngId)
                .collect(Collectors.toSet());
    }

    /** Doadores bloqueados por uma ONG (para o app da ONG desabilitar o chat). */
    public Set<Long> doadoresBloqueadosPor(Long ongId) {
        if (ongId == null) return Set.of();
        return repository.findByOngIdOrderByCriadoEmDesc(ongId).stream()
                .map(Bloqueio::getDoadorId)
                .collect(Collectors.toSet());
    }

    /**
     * ONGs que bloquearam o DOADOR AUTENTICADO da requisicao atual. Requisicao
     * anonima ou de conta ONG/admin devolve conjunto vazio (nao filtra nada) —
     * e o helper que os feeds usam para "sumir" com as ONGs bloqueadoras.
     */
    public Set<Long> ongIdsQueBloquearamDoadorAtual() {
        UsuarioAutenticado quem = security.atual();
        if (quem == null || !"DOADOR".equals(quem.getTipo())) {
            return Set.of();
        }
        return ongsQueBloquearam(quem.getId());
    }

    // ===================== HELPERS =====================

    /** Garante que quem chama e uma conta de ONG e devolve o ongId DO TOKEN. */
    private Long exigirOngAutenticada() {
        Long ongId = security.ongIdAtual();
        if (ongId == null) {
            throw new AcessoNegadoException(
                    "Apenas contas de ONG podem gerenciar bloqueios.");
        }
        return ongId;
    }

    private ResponseEntity<?> mensagem(String texto) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("mensagem", texto);
        return ResponseEntity.ok(corpo);
    }

    private ResponseEntity<?> erro(String texto) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", texto);
        return ResponseEntity.badRequest().body(corpo);
    }
}
