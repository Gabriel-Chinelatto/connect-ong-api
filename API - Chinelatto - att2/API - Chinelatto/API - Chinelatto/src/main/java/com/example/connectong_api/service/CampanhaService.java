package com.example.connectong_api.service;

import com.example.connectong_api.dto.CampanhaRequestDTO;
import com.example.connectong_api.dto.CampanhaResponseDTO;
import com.example.connectong_api.model.Campanha;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.CampanhaRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.UsuarioRepository;
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
 * Gerencia as campanhas de arrecadacao das ONGs (criar, listar, destaques).
 * Regra de negocio central: cada contribuicao incrementa o valorArrecadado e,
 * ao atingir a metaValor, a campanha e automaticamente encerrada. Notifica a
 * conta da ONG dona a cada contribuicao; campanhas orfas (sem ONG) sao ignoradas.
 */
@Service
public class CampanhaService {

    @Autowired private CampanhaRepository repository;
    @Autowired private ONGRepository ongRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NotificacaoService notificacaoService;
    @Autowired private AtividadeService atividadeService;
    @Autowired private SecurityUtils security;
    @Autowired private BloqueioService bloqueioService;
    @Autowired private RateLimitService rateLimitService;

    // Teto por contribuicao (mesmo limite da doacao financeira via DTO).
    private static final double VALOR_MAXIMO_CONTRIBUICAO = 1_000_000.00;

    // Teto GENEROSO de contribuicoes por IP na janela do rate limiting: nao
    // atrapalha a demonstracao da feira, mas impede um script de inflar/encerrar
    // campanhas com dezenas de contribuicoes simuladas. Configuravel para os
    // testes (que compartilham o IP 127.0.0.1) desligarem o limite.
    @org.springframework.beans.factory.annotation.Value("${app.ratelimit.max-contribuicoes:30}")
    private int maxContribuicoes;

    // =========================
    // LISTAR
    // =========================
    public List<CampanhaResponseDTO> listar(Long ongId, boolean somenteAbertas, String categoria) {
        List<Campanha> lista;
        // PERFORMANCE: variantes com JOIN FETCH da ONG — sem elas o Hibernate
        // dispara 1 consulta por ONG para preencher c.getOng() (usado no DTO),
        // o que custava ~2,9s com o banco longe do servidor. Mesma ordenacao.
        if (ongId != null) {
            lista = repository.findByOngIdComOng(ongId);
        } else if (somenteAbertas) {
            lista = repository.findAbertasComOng();
        } else {
            lista = repository.findAllComOng();
        }
        // BLOQUEIO: para um DOADOR autenticado, campanhas de ONGs que o
        // bloquearam somem do feed (anonimo/ONG = conjunto vazio, nao filtra).
        java.util.Set<Long> ongsBloqueadoras =
                bloqueioService.ongIdsQueBloquearamDoadorAtual();

        return lista.stream()
                .filter(c -> c.getOng() != null) // ignora campanhas orfas (dados legados)
                .filter(c -> !ongsBloqueadoras.contains(c.getOng().getId()))
                // filtro por categoria em memoria (compara valores normalizados)
                .filter(c -> categoria == null || categoria.isBlank()
                        || Categorias.iguais(c.getCategoria(), categoria))
                .map(CampanhaResponseDTO::new)
                .collect(Collectors.toList());
    }

    public List<CampanhaResponseDTO> destaques() {
        // Mesmo filtro de bloqueio do feed (o carrossel tambem "some").
        java.util.Set<Long> ongsBloqueadoras =
                bloqueioService.ongIdsQueBloquearamDoadorAtual();
        return repository.findByDestaqueTrueAndEncerradaFalseOrderByIdDesc()
                .stream()
                .filter(c -> c.getOng() != null)
                .filter(c -> !ongsBloqueadoras.contains(c.getOng().getId()))
                .map(CampanhaResponseDTO::new)
                .collect(Collectors.toList());
    }

    // =========================
    // CRIAR
    // =========================
    public ResponseEntity<?> criar(CampanhaRequestDTO dto) {
        // So a propria ONG dona pode criar campanhas em seu nome (senao 403).
        security.exigirOng(dto.getOngId());

        Ong ong = ongRepository.findById(dto.getOngId()).orElse(null);
        if (ong == null) return erro("ONG nao encontrada");

        Campanha c = new Campanha();
        c.setTitulo(dto.getTitulo());
        c.setDescricao(dto.getDescricao());
        c.setMetaValor(dto.getMetaValor());
        c.setCategoria(Categorias.normalizar(dto.getCategoria()));
        c.setDataInicio(dto.getDataInicio());
        c.setDataFim(dto.getDataFim());
        c.setDestaque(dto.getDestaque() != null && dto.getDestaque());
        c.setOng(ong);

        Campanha salva = repository.save(c);

        atividadeService.registrar(
                "CAMPANHA",
                ong.getNome() + " lancou a campanha \"" + salva.getTitulo() + "\"",
                ong.getId(),
                ong.getNome());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CampanhaResponseDTO(salva));
    }

    // =========================
    // CONTRIBUIR (incrementa o arrecadado; encerra ao bater a meta)
    // =========================
    public ResponseEntity<?> contribuir(Long id, Double valor, String doadorNomeIgnorado) {
        if (valor == null || valor <= 0) return erro("O valor deve ser maior que zero");
        if (valor > VALOR_MAXIMO_CONTRIBUICAO) return erro("Valor acima do limite permitido");

        // Anti-spam por IP (teto generoso): impede inflar/encerrar campanhas em massa.
        if (rateLimitService.excedeuSolicitacoes("contribuir", maxContribuicoes)) {
            return RateLimitService.resposta429();
        }

        Campanha c = repository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();
        if (c.getEncerrada()) return erro("Esta campanha ja foi encerrada");

        // O nome exibido na notificacao da ONG vem da IDENTIDADE DO TOKEN, nao do
        // corpo: antes o "doadorNome" chegava do cliente e era spoofavel (dava
        // para se passar por outra pessoa na notificacao). O corpo agora e ignorado.
        String doadorNome = nomeDoAutenticado();

        Campanha salva = registrarContribuicao(c, valor, doadorNome);

        return ResponseEntity.ok(new CampanhaResponseDTO(salva));
    }

    // Nome do usuario autenticado (para a notificacao), resolvido pelo id do
    // token — nunca por dado vindo do cliente. Null cai em "Alguem" no texto.
    private String nomeDoAutenticado() {
        Long uid = security.usuarioId();
        if (uid == null) return null;
        return usuarioRepository.findById(uid).map(Usuario::getNome).orElse(null);
    }

    // Nucleo da contribuicao, tambem reaproveitado pela doacao PIX vinculada a
    // campanha (DoacaoFinanceiraService): incrementa o arrecadado, auto-encerra
    // ao bater a meta e notifica a conta da ONG dona. As validacoes (campanha
    // existe/aberta, valor > 0) sao responsabilidade de quem chama.
    public Campanha registrarContribuicao(Campanha c, Double valor, String doadorNome) {
        c.setValorArrecadado(c.getValorArrecadado() + valor);
        boolean atingiuMeta = c.getMetaValor() != null
                && c.getValorArrecadado() >= c.getMetaValor();
        if (atingiuMeta) c.setEncerrada(true);

        Campanha salva = repository.save(c);

        // notifica a conta da ONG dona da campanha
        if (c.getOng() != null) {
            usuarioRepository.findByOngId(c.getOng().getId()).ifPresent(ongUser -> {
                String quem = (doadorNome != null && !doadorNome.isBlank())
                        ? doadorNome : "Alguem";
                String msg = atingiuMeta
                        ? "A campanha \"" + c.getTitulo() + "\" atingiu a meta!"
                        : quem + " contribuiu com R$ " + String.format("%.2f", valor)
                                + " na campanha \"" + c.getTitulo() + "\".";
                notificacaoService.criar(ongUser.getId(), "Campanha", msg, "CAMPANHA");
            });
        }

        // feed global: so registra o marco de meta atingida (evita ruido)
        if (atingiuMeta && c.getOng() != null) {
            atividadeService.registrar(
                    "CAMPANHA",
                    "A campanha \"" + c.getTitulo() + "\" atingiu a meta!",
                    c.getOng().getId(),
                    c.getOng().getNome());
        }

        return salva;
    }

    // =========================
    // ENCERRAR
    // =========================
    public ResponseEntity<?> encerrar(Long id) {
        Campanha c = repository.findById(id).orElse(null);
        if (c == null) return ResponseEntity.notFound().build();

        // So a ONG dona da campanha pode encerra-la (senao 403).
        Long ongId = c.getOng() != null ? c.getOng().getId() : null;
        security.exigirOng(ongId);

        c.setEncerrada(true);
        return ResponseEntity.ok(new CampanhaResponseDTO(repository.save(c)));
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
