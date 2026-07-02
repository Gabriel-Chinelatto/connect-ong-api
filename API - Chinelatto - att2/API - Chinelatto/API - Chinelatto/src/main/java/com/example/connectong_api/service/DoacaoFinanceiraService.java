package com.example.connectong_api.service;

import com.example.connectong_api.dto.DoacaoFinanceiraRequestDTO;
import com.example.connectong_api.dto.DoacaoFinanceiraResponseDTO;
import com.example.connectong_api.model.DoacaoFinanceira;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.DoacaoFinanceiraRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Processa doacoes em dinheiro (PIX simulado) de um doador para uma ONG.
 * Ao doar, gera um codigo PIX "copia e cola" fake como comprovante, registra a
 * operacao na auditoria e notifica a conta da ONG. O feed publico omite valor e
 * doador por privacidade; o comprovante completo so volta para quem doou.
 */
@Service
public class DoacaoFinanceiraService {

    @Autowired
    private DoacaoFinanceiraRepository repository;

    @Autowired
    private ONGRepository ongRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AtividadeService atividadeService;

    public List<DoacaoFinanceiraResponseDTO> listarPorDoador(Long doadorId) {
        return repository.findByDoadorIdOrderByDataCriacaoDesc(doadorId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<DoacaoFinanceiraResponseDTO> listarPorOng(Long ongId) {
        // A ONG ve quem doou e quanto, mas NAO o codigoPix (comprovante "copia e
        // cola" e privado do doador). Antes o mesmo toDTO vazava o codigoPix aqui.
        return repository.findByOngIdOrderByDataCriacaoDesc(ongId)
                .stream().map(this::toDTOSemComprovante).collect(Collectors.toList());
    }

    // Registra a doacao financeira (PIX simulado) e gera o comprovante.
    public ResponseEntity<?> doar(DoacaoFinanceiraRequestDTO dto) {
        if (dto.getOngId() == null || dto.getDoadorId() == null) {
            return erro("É obrigatório informar ongId e doadorId");
        }
        if (dto.getValor() == null || dto.getValor() <= 0) {
            return erro("O valor deve ser maior que zero");
        }

        Ong ong = ongRepository.findById(dto.getOngId()).orElse(null);
        if (ong == null) return erro("ONG não encontrada");

        Usuario doador = usuarioRepository.findById(dto.getDoadorId()).orElse(null);
        if (doador == null) return erro("Doador não encontrado");

        DoacaoFinanceira doacao = new DoacaoFinanceira();
        doacao.setOngId(ong.getId());
        doacao.setOngNome(ong.getNome());
        doacao.setDoadorId(doador.getId());
        doacao.setDoadorNome(doador.getNome());
        doacao.setValor(dto.getValor());
        doacao.setCodigoPix(gerarCodigoPix(dto.getValor()));

        DoacaoFinanceira salva = repository.save(doacao);

        auditService.registrar("DOACAO_FINANCEIRA", doador.getId(),
                "Doacao PIX de R$ " + String.format("%.2f", dto.getValor())
                        + " para " + ong.getNome() + " (ongId=" + ong.getId() + ")");

        // notifica a ONG (conta de login vinculada, se houver)
        usuarioRepository.findByOngId(ong.getId()).ifPresent(ongUser ->
                notificacaoService.criar(
                        ongUser.getId(),
                        "Doação recebida",
                        doador.getNome() + " doou R$ "
                                + String.format("%.2f", dto.getValor()) + " via PIX.",
                        "PRESTACAO"));

        // feed global: sem valor/doador (privacidade num feed publico)
        atividadeService.registrar(
                "DOACAO",
                ong.getNome() + " recebeu uma nova doacao via PIX",
                ong.getId(),
                ong.getNome());

        return ResponseEntity.status(HttpStatus.CREATED).body(toDTO(salva));
    }

    // Gera um codigo "copia e cola" simulado (parecido com PIX, mas fake).
    private String gerarCodigoPix(Double valor) {
        String token = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return "00020126SIMULADO" + token.substring(0, 20)
                + String.format("%.0f", valor * 100);
    }

    private DoacaoFinanceiraResponseDTO toDTO(DoacaoFinanceira d) {
        return new DoacaoFinanceiraResponseDTO(
                d.getId(),
                d.getOngId(),
                d.getOngNome(),
                d.getDoadorNome(),
                d.getValor(),
                d.getCodigoPix(),
                d.getStatus(),
                d.getDataCriacao()
        );
    }

    // Visao da ONG: igual ao toDTO, mas sem o codigoPix (comprovante do doador).
    private DoacaoFinanceiraResponseDTO toDTOSemComprovante(DoacaoFinanceira d) {
        return new DoacaoFinanceiraResponseDTO(
                d.getId(),
                d.getOngId(),
                d.getOngNome(),
                d.getDoadorNome(),
                d.getValor(),
                null,
                d.getStatus(),
                d.getDataCriacao()
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
