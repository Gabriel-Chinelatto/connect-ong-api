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
    @Autowired private AvaliacaoRepository avaliacaoRepository;
    @Autowired private PrestacaoRepository prestacaoRepository;
    @Autowired private DoacaoFinanceiraRepository doacaoFinanceiraRepository;
    @Autowired private BCryptPasswordEncoder encoder;

    private static final String SENHA_DEMO = "demo123";
    private static final String MARCADOR = "demo.larviva@connectong.com";

    @Transactional
    public Map<String, Object> carregar() {
        // Idempotencia: se o marcador ja existe, nao recria nada.
        if (usuarioRepository.findByEmail(MARCADOR).isPresent()) {
            return resumo("ja_carregado");
        }

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

        // ----- Chat no match Joao x Lar Viva -----
        criarMensagem(iJoao, "DOADOR",
                "Ola! Tenho fraldas tamanho G para doar, quando posso levar?");
        criarMensagem(iJoao, "ONG",
                "Que otimo! Pode trazer na quinta de manha. Muito obrigada!");

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

        return resumo("carregado");
    }

    // ----------------------------------------------------------- helpers

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

    private void criarMensagem(Interesse interesse, String remetente, String conteudo) {
        Mensagem m = new Mensagem();
        m.setInteresse(interesse);
        m.setRemetente(remetente);
        m.setConteudo(conteudo);
        mensagemRepository.save(m);
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
