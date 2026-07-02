package com.example.connectong_api.service;

import com.example.connectong_api.model.*;
import com.example.connectong_api.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Modo Feira": popula o sistema com dados demonstrativos realistas para a
 * apresentacao (FECITEC). Idempotente — se ja foi carregado, nao duplica.
 *
 * Todas as contas demo usam a senha padrao "demo123". Nenhuma string salva
 * tem emoji (MySQL 5.6 utf8 nao aceita 4 bytes).
 */
@Service
public class DemoService {

    @Autowired private ONGRepository ongRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private MensagemRepository mensagemRepository;
    @Autowired private ReacaoRepository reacaoRepository;
    @Autowired private AvaliacaoRepository avaliacaoRepository;
    @Autowired private PrestacaoRepository prestacaoRepository;
    @Autowired private DoacaoFinanceiraRepository doacaoFinanceiraRepository;
    @Autowired private CampanhaRepository campanhaRepository;
    @Autowired private AtividadeRepository atividadeRepository;
    @Autowired private AtividadeService atividadeService;
    @Autowired private BCryptPasswordEncoder encoder;

    private static final String SENHA_DEMO = "demo123";
    private static final String MARCADOR = "demo.larviva@connectong.com";

    @Transactional
    public Map<String, Object> carregar() {
        boolean baseExiste = usuarioRepository.findByEmail(MARCADOR).isPresent();

        Ong larViva, criancaFeliz, patinhas;

        if (baseExiste) {
            // Dados base ja existem: recupera as ONGs para (re)garantir campanhas.
            larViva = ongDaConta(MARCADOR);
            criancaFeliz = ongDaConta("demo.criancafeliz@connectong.com");
            patinhas = ongDaConta("demo.patinhas@connectong.com");
        } else {
            criarBase();
            larViva = ongDaConta(MARCADOR);
            criancaFeliz = ongDaConta("demo.criancafeliz@connectong.com");
            patinhas = ongDaConta("demo.patinhas@connectong.com");
        }

        // ----- Campanhas (idempotentes por titulo) -----
        ensureCampanha(larViva, "Inverno Solidario",
                "Arrecadacao de cobertores e agasalhos para os idosos.",
                1000.0, 350.0, "Roupas", true);
        ensureCampanha(criancaFeliz, "Volta as Aulas",
                "Kits escolares completos para 50 criancas.",
                2000.0, 1200.0, "Educacao", true);
        ensureCampanha(patinhas, "Castracao Solidaria",
                "Castracao de 30 animais resgatados.",
                1500.0, 400.0, "Saude", false);

        // ----- Timeline (feed global) — so popula se estiver vazia -----
        ensureAtividadesDemo(larViva, criancaFeliz, patinhas);

        return resumo(baseExiste ? "campanhas_garantidas" : "carregado");
    }

    private void criarBase() {
        // ----- ONGs (perfil + conta de login) -----
        Ong larViva = criarOng("Lar Viva", MARCADOR, "(19) 3441-1000",
                "Limeira", "Acolhe idosos em situacao de vulnerabilidade.", true);
        Ong criancaFeliz = criarOng("Instituto Crianca Feliz",
                "demo.criancafeliz@connectong.com", "(19) 3232-2000", "Campinas",
                "Apoio educacional a criancas carentes.", true);
        Ong patinhas = criarOng("Abrigo Patinhas",
                "demo.patinhas@connectong.com", "(19) 3422-3000", "Piracicaba",
                "Resgate e cuidado de animais abandonados.", false);
        Ong renascer = criarOng("Casa Renascer",
                "demo.renascer@connectong.com", "(19) 3441-4000", "Limeira",
                "Reinsercao social de pessoas em recuperacao.", false);

        // ----- Doadores -----
        Usuario joao = criarDoador("Joao Pereira", "demo.joao@connectong.com");
        Usuario ana = criarDoador("Ana Costa", "demo.ana@connectong.com");
        Usuario empresa =
                criarDoador("Tech Solutions LTDA", "demo.empresa@connectong.com");

        // ----- Necessidades -----
        Necessidade nFraldas = criarNecessidade(larViva, "Fraldas geriatricas",
                "Precisamos de fraldas tamanho G para nossos idosos.", "Higiene", true);
        criarNecessidade(larViva, "Cobertores",
                "Cobertores novos ou usados em bom estado para o inverno.", "Roupas", false);
        Necessidade nMaterial = criarNecessidade(criancaFeliz, "Material escolar",
                "Cadernos, lapis e mochilas para o ano letivo.", "Educacao", true);
        criarNecessidade(criancaFeliz, "Brinquedos educativos",
                "Jogos e brinquedos pedagogicos.", "Brinquedos", false);
        criarNecessidade(criancaFeliz, "Leite em po",
                "Leite para o lanche das criancas.", "Alimentos", false);
        Necessidade nRacao = criarNecessidade(patinhas, "Racao para caes",
                "Racao para alimentar 40 caes resgatados.", "Alimentos", true);
        criarNecessidade(patinhas, "Medicamentos veterinarios",
                "Vermifugos e antipulgas.", "Saude", false);
        criarNecessidade(renascer, "Cestas basicas",
                "Cestas para as familias atendidas.", "Alimentos", false);
        criarNecessidade(renascer, "Roupas adultas",
                "Roupas masculinas e femininas.", "Roupas", false);

        // ----- Interesses / matches -----
        Interesse iJoao = criarInteresse(nFraldas, joao, "ACEITO");
        criarInteresse(nMaterial, ana, "ACEITO");
        criarInteresse(nRacao, empresa, "PENDENTE");

        // ----- Chat no match Joao x Lar Viva (com "visto" e reacoes) -----
        // A conta de login (Usuario) da ONG Lar Viva, para as reacoes do lado ONG.
        Usuario larVivaUser = usuarioRepository.findByOngId(larViva.getId()).orElse(null);

        Mensagem m1 = criarMensagem(iJoao, "DOADOR",
                "Ola! Tenho fraldas tamanho G para doar, quando posso levar?");
        Mensagem m2 = criarMensagem(iJoao, "ONG",
                "Que otimo! Pode trazer na quinta de manha. Muito obrigada!");
        Mensagem m3 = criarMensagem(iJoao, "DOADOR",
                "Perfeito, levo umas 5 embalagens entao.");
        // Ultima mensagem fica SEM recibo de leitura (aparece com 1 check).
        criarMensagem(iJoao, "ONG",
                "Combinado! Deixei anotado na recepcao. Ate quinta!");

        // "Visto": as tres primeiras ja foram lidas pelo outro lado.
        marcarLida(m1);
        marcarLida(m2);
        marcarLida(m3);

        // Reacoes (codigo utf8-safe; o app renderiza o emoji):
        // a ONG amou a oferta do doador; o doador agradeceu com uma "prece".
        if (larVivaUser != null) {
            criarReacao(m1, larVivaUser.getId(), "ONG", "LOVE");
        }
        criarReacao(m2, joao.getId(), "DOADOR", "PRAY");

        // ----- Prestacao de contas -----
        criarPrestacao(iJoao, "Fraldas entregues",
                "As fraldas doadas ja estao ajudando nossos idosos. Gratidao pela parceria!");

        // ----- Avaliacoes (atualizam a nota media da ONG) -----
        criarAvaliacao(larViva, joao, 5,
                "Atendimento maravilhoso, a doacao fez muita diferenca!");
        criarAvaliacao(criancaFeliz, ana, 4,
                "Equipe muito atenciosa e transparente.");

        // ----- Doacoes financeiras (PIX) -----
        criarDoacao(criancaFeliz, empresa, 250.0);
        criarDoacao(larViva, joao, 100.0);
    }

    // ----------------------------------------------------------- helpers

    // Popula a timeline com eventos representativos (um por tipo) apenas se o
    // feed estiver vazio — idempotente e nao conflita com atividades reais.
    private void ensureAtividadesDemo(Ong larViva, Ong criancaFeliz, Ong patinhas) {
        if (atividadeRepository.count() > 0) return;

        if (criancaFeliz != null) {
            atividadeService.registrar("NECESSIDADE",
                    criancaFeliz.getNome() + " publicou uma nova necessidade: \"Material escolar\"",
                    criancaFeliz.getId(), criancaFeliz.getNome());
        }
        if (patinhas != null) {
            atividadeService.registrar("INTERESSE",
                    "Alguem demonstrou interesse em \"Racao para caes\"",
                    patinhas.getId(), patinhas.getNome());
        }
        if (larViva != null) {
            atividadeService.registrar("INTERESSE",
                    "Novo match formado em \"Fraldas geriatricas\"",
                    larViva.getId(), larViva.getNome());
            atividadeService.registrar("PRESTACAO",
                    "Nova prestacao de contas: \"Fraldas entregues\"",
                    larViva.getId(), larViva.getNome());
            atividadeService.registrar("DOACAO",
                    larViva.getNome() + " recebeu uma nova doacao via PIX",
                    larViva.getId(), larViva.getNome());
            atividadeService.registrar("AVALIACAO",
                    larViva.getNome() + " recebeu uma nova avaliacao",
                    larViva.getId(), larViva.getNome());
        }
        if (criancaFeliz != null) {
            atividadeService.registrar("CAMPANHA",
                    criancaFeliz.getNome() + " lancou a campanha \"Volta as Aulas\"",
                    criancaFeliz.getId(), criancaFeliz.getNome());
        }
    }

    private Ong ongDaConta(String email) {
        return usuarioRepository.findByEmail(email)
                .map(u -> u.getOngId())
                .flatMap(ongRepository::findById)
                .orElse(null);
    }

    private void ensureCampanha(Ong ong, String titulo, String descricao,
                                double meta, double arrecadado,
                                String categoria, boolean destaque) {
        if (ong == null) return;
        boolean jaExiste = campanhaRepository.findByOngIdOrderByIdDesc(ong.getId())
                .stream().anyMatch(c -> titulo.equals(c.getTitulo()));
        if (jaExiste) return;

        Campanha c = new Campanha();
        c.setTitulo(titulo);
        c.setDescricao(descricao);
        c.setMetaValor(meta);
        c.setValorArrecadado(arrecadado);
        c.setCategoria(categoria);
        c.setDestaque(destaque);
        c.setEncerrada(false);
        c.setOng(ong);
        campanhaRepository.save(c);
    }

    private Ong criarOng(String nome, String email, String tel, String cidade,
                         String descricao, boolean verificada) {
        Ong ong = new Ong(nome, email, tel, cidade, descricao);
        ong.setVerificada(verificada);
        ong = ongRepository.save(ong);

        Usuario conta = new Usuario();
        conta.setNome(nome);
        conta.setEmail(email);
        conta.setSenha(encoder.encode(SENHA_DEMO));
        conta.setTipo("ONG");
        conta.setOngId(ong.getId());
        usuarioRepository.save(conta);
        return ong;
    }

    private Usuario criarDoador(String nome, String email) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha(encoder.encode(SENHA_DEMO));
        u.setTipo("DOADOR");
        return usuarioRepository.save(u);
    }

    private Necessidade criarNecessidade(Ong ong, String titulo, String desc,
                                         String categoria, boolean urgente) {
        Necessidade n = new Necessidade();
        n.setOng(ong);
        n.setTitulo(titulo);
        n.setDescricao(desc);
        n.setCategoria(categoria);
        n.setUrgente(urgente);
        return necessidadeRepository.save(n);
    }

    private Interesse criarInteresse(Necessidade nec, Usuario doador, String status) {
        Interesse i = new Interesse();
        i.setNecessidade(nec);
        i.setDoador(doador);
        i.setStatus(status);
        return interesseRepository.save(i);
    }

    private Mensagem criarMensagem(Interesse interesse, String remetente, String conteudo) {
        Mensagem m = new Mensagem();
        m.setInteresse(interesse);
        m.setRemetente(remetente);
        m.setConteudo(conteudo);
        return mensagemRepository.save(m);
    }

    // Marca a mensagem como lida agora (recibo de leitura "visto").
    private void marcarLida(Mensagem m) {
        m.setDataLeitura(java.time.LocalDateTime.now());
        mensagemRepository.save(m);
    }

    // Reacao (emoji) demo a uma mensagem. Guarda o CODIGO (LIKE/LOVE/...), nunca o
    // emoji cru (utf8 de 3 bytes do MySQL 5.6 nao aceita 4 bytes).
    private void criarReacao(Mensagem m, Long usuarioId, String lado, String emojiCodigo) {
        Reacao r = new Reacao();
        r.setMensagemId(m.getId());
        r.setUsuarioId(usuarioId);
        r.setLado(lado);
        r.setEmoji(emojiCodigo);
        reacaoRepository.save(r);
    }

    private void criarPrestacao(Interesse interesse, String titulo, String descricao) {
        Prestacao p = new Prestacao();
        p.setInteresse(interesse);
        p.setTitulo(titulo);
        p.setDescricao(descricao);
        p.setFotoUrl("");
        prestacaoRepository.save(p);
    }

    private void criarAvaliacao(Ong ong, Usuario doador, int nota, String comentario) {
        Avaliacao a = new Avaliacao();
        a.setOngId(ong.getId());
        a.setDoadorId(doador.getId());
        a.setDoadorNome(doador.getNome());
        a.setNota(nota);
        a.setComentario(comentario);
        avaliacaoRepository.save(a);

        // atualiza a media denormalizada da ONG (1 avaliacao por ONG no seed)
        ong.setNotaMedia((double) nota);
        ong.setTotalAvaliacoes(1);
        ongRepository.save(ong);
    }

    private void criarDoacao(Ong ong, Usuario doador, double valor) {
        DoacaoFinanceira d = new DoacaoFinanceira();
        d.setOngId(ong.getId());
        d.setOngNome(ong.getNome());
        d.setDoadorId(doador.getId());
        d.setDoadorNome(doador.getNome());
        d.setValor(valor);
        d.setCodigoPix("00020126SIMULADODEMO" + (long) (valor * 100));
        doacaoFinanceiraRepository.save(d);
    }

    private Map<String, Object> resumo(String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("senhaPadrao", SENHA_DEMO);
        m.put("contaOngExemplo", MARCADOR);
        m.put("contaDoadorExemplo", "demo.joao@connectong.com");
        m.put("totalOngs", ongRepository.count());
        m.put("totalUsuarios", usuarioRepository.count());
        m.put("totalNecessidades", necessidadeRepository.count());
        return m;
    }
}
