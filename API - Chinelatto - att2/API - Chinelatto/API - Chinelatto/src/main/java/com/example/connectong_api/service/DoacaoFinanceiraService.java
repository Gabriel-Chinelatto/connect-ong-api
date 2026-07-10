package com.example.connectong_api.service;

import com.example.connectong_api.dto.DoacaoFinanceiraRequestDTO;
import com.example.connectong_api.dto.DoacaoFinanceiraResponseDTO;
import com.example.connectong_api.model.Campanha;
import com.example.connectong_api.model.DoacaoFinanceira;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.CampanhaRepository;
import com.example.connectong_api.repository.DoacaoFinanceiraRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Processa doacoes em dinheiro (PIX simulado) de um doador para uma ONG.
 * FLUXO EM 2 FASES (feira): (1) gerarCodigo() cria o codigo "copia e cola"
 * fake com o valor embutido, SEM persistir nada; (2) doar() registra a doacao
 * CONFIRMADO reaproveitando o codigo recebido (ou gerando um, se nao veio).
 * Se a doacao veio com campanhaId valido, incrementa o valor arrecadado da
 * campanha (auto-encerrando ao bater a meta e notificando a ONG — mesma logica
 * de /campanhas/{id}/contribuir, reaproveitada do CampanhaService).
 * O feed publico omite valor e doador por privacidade; o comprovante completo
 * so volta para quem doou.
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
    private CampanhaRepository campanhaRepository;

    @Autowired
    private CampanhaService campanhaService;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private AtividadeService atividadeService;

    @Autowired
    private RateLimitService rateLimitService;

    // Teto GENEROSO de doacoes simuladas por IP na janela do rate limiting: nao
    // atrapalha a feira, mas impede que um doador use o PIX SIMULADO (sem
    // pagamento real) como um segundo caminho para inflar/encerrar em massa a
    // campanha de qualquer ONG (mesma logica de /campanhas/{id}/contribuir).
    // Configuravel para os testes (IP 127.0.0.1 compartilhado) desligarem o limite.
    @org.springframework.beans.factory.annotation.Value("${app.ratelimit.max-doacoes:30}")
    private int maxDoacoes;

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

    // FASE 1 do PIX simulado: gera o codigo "copia e cola" com o valor embutido.
    // STATELESS de proposito: nada e persistido aqui — a doacao so existe
    // quando a fase 2 (doar) confirma. Abandonar o fluxo nao deixa lixo.
    public ResponseEntity<?> gerarCodigo(Double valor) {
        if (valor == null || valor <= 0) {
            return erro("O valor deve ser maior que zero");
        }
        Map<String, String> corpo = new HashMap<>();
        corpo.put("codigoPix", gerarCodigoPix(valor));
        return ResponseEntity.ok(corpo);
    }

    // FASE 2: registra a doacao financeira (PIX simulado) e gera o comprovante.
    @Transactional
    public ResponseEntity<?> doar(DoacaoFinanceiraRequestDTO dto) {
        if (dto.getOngId() == null || dto.getDoadorId() == null) {
            return erro("É obrigatório informar ongId e doadorId");
        }
        if (dto.getValor() == null || dto.getValor() <= 0) {
            return erro("O valor deve ser maior que zero");
        }

        // Anti-spam por IP (teto generoso): impede usar o PIX simulado para
        // inflar/encerrar campanhas alheias em massa (ver MAX_DOACOES_JANELA).
        if (rateLimitService.excedeuSolicitacoes("doacao-financeira", maxDoacoes)) {
            return RateLimitService.resposta429();
        }

        Ong ong = ongRepository.findById(dto.getOngId()).orElse(null);
        if (ong == null) return erro("ONG não encontrada");

        Usuario doador = usuarioRepository.findById(dto.getDoadorId()).orElse(null);
        if (doador == null) return erro("Doador não encontrado");

        // Campanha (opcional): valida ANTES de registrar a doacao.
        Campanha campanha = null;
        if (dto.getCampanhaId() != null) {
            campanha = campanhaRepository.findById(dto.getCampanhaId()).orElse(null);
            if (campanha == null) return erro("Campanha não encontrada");
            Long ongDaCampanha = campanha.getOng() != null
                    ? campanha.getOng().getId() : null;
            if (!dto.getOngId().equals(ongDaCampanha)) {
                return erro("A campanha não pertence a esta ONG");
            }
            if (campanha.getEncerrada()) {
                return erro("Esta campanha já foi encerrada");
            }
        }

        DoacaoFinanceira doacao = new DoacaoFinanceira();
        doacao.setOngId(ong.getId());
        doacao.setOngNome(ong.getNome());
        doacao.setDoadorId(doador.getId());
        doacao.setDoadorNome(doador.getNome());
        doacao.setValor(dto.getValor());
        doacao.setCampanhaId(campanha != null ? campanha.getId() : null);
        // PIX em 2 fases: reaproveita o codigo gerado na fase 1, se veio.
        String codigo = (dto.getCodigoPix() != null && !dto.getCodigoPix().isBlank())
                ? dto.getCodigoPix()
                : gerarCodigoPix(dto.getValor());
        doacao.setCodigoPix(codigo);

        DoacaoFinanceira salva = repository.save(doacao);

        // Doacao de campanha: incrementa o arrecadado, auto-encerra ao bater a
        // meta e notifica a ONG (logica unica, compartilhada com /contribuir).
        if (campanha != null) {
            campanhaService.registrarContribuicao(campanha, dto.getValor(), doador.getNome());
        }

        auditService.registrar("DOACAO_FINANCEIRA", doador.getId(),
                "Doacao PIX de R$ " + String.format("%.2f", dto.getValor())
                        + " para " + ong.getNome() + " (ongId=" + ong.getId() + ")"
                        + (campanha != null ? " [campanhaId=" + campanha.getId() + "]" : ""));

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

    // Titulo da campanha vinculada (null quando a doacao nao e de campanha).
    private String tituloCampanha(Long campanhaId) {
        if (campanhaId == null) return null;
        return campanhaRepository.findById(campanhaId)
                .map(Campanha::getTitulo)
                .orElse(null);
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
                d.getDataCriacao(),
                d.getCampanhaId(),
                tituloCampanha(d.getCampanhaId())
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
                d.getDataCriacao(),
                d.getCampanhaId(),
                tituloCampanha(d.getCampanhaId())
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
