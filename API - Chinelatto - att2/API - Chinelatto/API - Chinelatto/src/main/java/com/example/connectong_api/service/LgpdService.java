package com.example.connectong_api.service;

import com.example.connectong_api.model.*;
import com.example.connectong_api.repository.*;
import com.example.connectong_api.security.ClientIpResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Direitos do titular de dados (LGPD, art. 18) — item F-04 do Plano de Acao,
 * apontado pelo avaliador da FECITEC ("nao tem ... LGPD").
 *
 * <ul>
 *   <li><b>Consentimento</b> (art. 8): cada cadastro grava o aceite dos Termos
 *       e da Politica, com versao, data e IP.</li>
 *   <li><b>Acesso e portabilidade</b> (art. 18, II e V): {@link #exportar}
 *       devolve TUDO o que o sistema guarda sobre a pessoa, num JSON legivel.</li>
 *   <li><b>Eliminacao</b> (art. 18, VI): {@link #anonimizar} apaga os dados
 *       pessoais da conta excluida. O HISTORICO continua (uma doacao feita
 *       continua contando para a ONG, uma conversa continua existindo para o
 *       outro lado), mas sem nada que identifique a pessoa — o art. 16 permite
 *       guardar dado anonimizado.</li>
 * </ul>
 */
@Service
public class LgpdService {

    public static final String NOME_ANONIMO = "Usuário removido";

    private final UsuarioRepository usuarioRepository;
    private final ConsentimentoRepository consentimentoRepository;
    private final PreferenciaRepository preferenciaRepository;
    private final InteresseRepository interesseRepository;
    private final MensagemRepository mensagemRepository;
    private final DoacaoFinanceiraRepository doacaoFinanceiraRepository;
    private final FavoritoRepository favoritoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final AuditLogRepository auditLogRepository;
    private final ONGRepository ongRepository;
    private final ClientIpResolver ipResolver;
    private final JdbcTemplate jdbc;

    public LgpdService(UsuarioRepository usuarioRepository,
                       ConsentimentoRepository consentimentoRepository,
                       PreferenciaRepository preferenciaRepository,
                       InteresseRepository interesseRepository,
                       MensagemRepository mensagemRepository,
                       DoacaoFinanceiraRepository doacaoFinanceiraRepository,
                       FavoritoRepository favoritoRepository,
                       NotificacaoRepository notificacaoRepository,
                       AuditLogRepository auditLogRepository,
                       ONGRepository ongRepository,
                       ClientIpResolver ipResolver,
                       JdbcTemplate jdbc) {
        this.usuarioRepository = usuarioRepository;
        this.consentimentoRepository = consentimentoRepository;
        this.preferenciaRepository = preferenciaRepository;
        this.interesseRepository = interesseRepository;
        this.mensagemRepository = mensagemRepository;
        this.doacaoFinanceiraRepository = doacaoFinanceiraRepository;
        this.favoritoRepository = favoritoRepository;
        this.notificacaoRepository = notificacaoRepository;
        this.auditLogRepository = auditLogRepository;
        this.ongRepository = ongRepository;
        this.ipResolver = ipResolver;
        this.jdbc = jdbc;
    }

    // ===================== CONSENTIMENTO =====================

    /** Grava o aceite da versao vigente dos Termos e da Politica de Privacidade. */
    public void registrarConsentimento(Long usuarioId) {
        consentimentoRepository.save(new Consentimento(usuarioId,
                Consentimento.DOCUMENTO_TERMOS, Consentimento.VERSAO_ATUAL, ipResolver.resolve()));
    }

    public List<Map<String, Object>> consentimentos(Long usuarioId) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Consentimento c : consentimentoRepository.findByUsuarioIdOrderByAceitoEmDesc(usuarioId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("documento", c.getDocumento());
            m.put("versao", c.getVersao());
            m.put("aceitoEm", c.getAceitoEm());
            m.put("versaoVigente", Consentimento.VERSAO_ATUAL.equals(c.getVersao()));
            lista.add(m);
        }
        return lista;
    }

    // ===================== ACESSO E PORTABILIDADE =====================

    /**
     * Tudo o que o sistema guarda sobre o titular. Monta mapas (e nao devolve as
     * entidades) para nunca vazar a senha, relacoes preguicosas nem dados de
     * terceiros alem do necessario para entender o registro.
     */
    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> exportar(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return Optional.empty();

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("sobre", "Exportação dos seus dados pessoais no Connect ONG (LGPD, art. 18, incisos II e V). "
                + "A senha não aparece porque o sistema não a conhece: guarda só um hash BCrypt.");
        dados.put("geradoEm", LocalDateTime.now());

        Map<String, Object> conta = new LinkedHashMap<>();
        conta.put("id", u.getId());
        conta.put("nome", u.getNome());
        conta.put("email", u.getEmail());
        conta.put("tipo", u.getTipo());
        conta.put("telefone", u.getTelefone());
        conta.put("cidade", u.getCidade());
        conta.put("estado", u.getEstado());
        conta.put("bio", u.getBio());
        conta.put("temFoto", u.getFotoBase64() != null || u.getFotoUrl() != null);
        conta.put("criadaEm", u.getCriadoEm());
        conta.put("ultimoAcesso", u.getUltimoVisto());
        dados.put("conta", conta);

        if (u.getOngId() != null) {
            ongRepository.findById(u.getOngId()).ifPresent(ong -> {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", ong.getId());
                o.put("nome", ong.getNome());
                o.put("email", ong.getEmail());
                o.put("telefone", ong.getTelefone());
                o.put("cnpj", ong.getCnpj());
                o.put("cidade", ong.getCidade());
                o.put("endereco", ong.getEndereco());
                o.put("descricao", ong.getDescricao());
                dados.put("ong", o);
            });
        }

        dados.put("consentimentos", consentimentos(usuarioId));
        preferenciaRepository.findByUsuarioId(usuarioId).ifPresent(p -> dados.put("preferencias", p));

        List<Map<String, Object>> matches = new ArrayList<>();
        for (Interesse i : interesseRepository.findByDoadorId(usuarioId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("necessidade", i.getNecessidade() != null ? i.getNecessidade().getTitulo() : null);
            m.put("ong", i.getNecessidade() != null && i.getNecessidade().getOng() != null
                    ? i.getNecessidade().getOng().getNome() : null);
            m.put("status", i.getStatus());
            m.put("criadoEm", i.getDataCriacao());
            List<Map<String, Object>> conversa = new ArrayList<>();
            for (Mensagem msg : mensagemRepository.findByInteresseIdOrderByDataEnvioAsc(i.getId())) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("de", "DOADOR".equals(msg.getRemetente()) ? "você" : "ONG");
                mm.put("texto", msg.getConteudo());
                mm.put("temAnexo", msg.getAnexoBase64() != null);
                mm.put("enviadaEm", msg.getDataEnvio());
                conversa.add(mm);
            }
            m.put("conversa", conversa);
            matches.add(m);
        }
        dados.put("interessesEConversas", matches);

        List<Map<String, Object>> doacoes = new ArrayList<>();
        for (DoacaoFinanceira d : doacaoFinanceiraRepository.findByDoadorIdOrderByDataCriacaoDesc(usuarioId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ong", d.getOngNome());
            m.put("valor", d.getValor());
            m.put("status", d.getStatus());
            m.put("data", d.getDataCriacao());
            doacoes.add(m);
        }
        dados.put("doacoesPix", doacoes);

        List<Map<String, Object>> favoritos = new ArrayList<>();
        for (Favorito f : favoritoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId)) {
            favoritos.add(Map.of("tipo", f.getTipo(), "alvoId", f.getAlvoId()));
        }
        dados.put("favoritos", favoritos);

        List<Map<String, Object>> notificacoes = new ArrayList<>();
        for (Notificacao n : notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(usuarioId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("titulo", n.getTitulo());
            m.put("mensagem", n.getMensagem());
            m.put("data", n.getDataCriacao());
            notificacoes.add(m);
        }
        dados.put("notificacoes", notificacoes);

        // Registro de acesso: o titular tem direito de saber quando e de onde a
        // conta foi usada (e ajuda a perceber um acesso que nao foi ele).
        List<Map<String, Object>> acessos = new ArrayList<>();
        for (AuditLog a : auditLogRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("acao", a.getAcao());
            m.put("ip", a.getIp());
            m.put("data", a.getCriadoEm());
            acessos.add(m);
        }
        dados.put("registroDeAcessos", acessos);
        return Optional.of(dados);
    }

    // ===================== ELIMINACAO =====================

    /**
     * Apaga os dados pessoais da conta (chamado ao excluir a conta). O e-mail
     * vira um endereco impossivel e unico (dominio .invalid, RFC 2606), o que
     * tambem libera o e-mail original para um cadastro novo. Nomes copiados em
     * outras tabelas (avaliacao, doacao) tambem sao trocados.
     */
    @Transactional
    public void anonimizar(Usuario u) {
        u.setNome(NOME_ANONIMO);
        u.setEmail("removido-" + u.getId() + "@anonimizado.invalid");
        u.setTelefone(null);
        u.setCidade(null);
        u.setEstado(null);
        u.setBio(null);
        u.setFotoUrl(null);
        u.setFotoBase64(null);
        usuarioRepository.save(u);

        jdbc.update("UPDATE avaliacao SET doador_nome = ? WHERE doador_id = ?", NOME_ANONIMO, u.getId());
        jdbc.update("UPDATE doacao_financeira SET doador_nome = ? WHERE doador_id = ?", NOME_ANONIMO, u.getId());
        // Favoritos e notificacoes sao so do titular: nao servem a mais ninguem.
        favoritoRepository.deleteAll(favoritoRepository.findByUsuarioIdOrderByDataCriacaoDesc(u.getId()));
        notificacaoRepository.deleteAll(notificacaoRepository.findByUsuarioIdOrderByDataCriacaoDesc(u.getId()));
    }
}
