package com.example.connectong_api;

import com.example.connectong_api.dto.SobreOngRequestDTO;
import com.example.connectong_api.dto.SobreOngResponseDTO;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.NecessidadeRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.service.SobreOngService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do SobreOngService no modo REGRAS (sem chave de IA na CI): compoe o
 * "Sobre" institucional a partir do rascunho + dados reais da ONG, sempre
 * devolvendo algo. Roda com H2.
 */
@SpringBootTest
class SobreOngRegrasTest {

    @Autowired private SobreOngService sobreOngService;
    @Autowired private ONGRepository ongRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    private Ong criarOngComNecessidade() {
        long n = SEQ.getAndIncrement();
        Ong ong = new Ong("Casa do Sol " + n, "casasol" + n + "@sobre.test",
                "1930000" + n, "Limeira", "Acolhe familias.");
        ong.setVerificada(true);
        ong = ongRepository.save(ong);

        Necessidade nec = new Necessidade();
        nec.setOng(ong);
        nec.setTitulo("Cestas basicas " + n);
        nec.setCategoria("Alimentos");
        necessidadeRepository.save(nec);
        return ong;
    }

    @Test
    void sobre_usaNomeCidadeERascunho_devolveDescricaoNaoVazia() {
        Ong ong = criarOngComNecessidade();

        SobreOngRequestDTO req = new SobreOngRequestDTO();
        req.setOngId(ong.getId());
        req.setRascunho("ajudamos criancas e idosos em situacao de vulnerabilidade");

        SobreOngResponseDTO resp = sobreOngService.gerar(req);

        assertNotNull(resp.getDescricao());
        assertFalse(resp.getDescricao().isBlank(), "descricao nao deveria ser vazia");
        assertEquals("regras", resp.getModo());
        // Usa os DADOS REAIS: nome e cidade da ONG.
        assertTrue(resp.getDescricao().contains(ong.getNome()),
                "deveria citar o nome da ONG: " + resp.getDescricao());
        assertTrue(resp.getDescricao().contains("Limeira"),
                "deveria citar a cidade da ONG: " + resp.getDescricao());
        // Reaproveita o rascunho.
        assertTrue(resp.getDescricao().toLowerCase().contains("criancas"),
                "deveria reaproveitar o rascunho: " + resp.getDescricao());
    }

    @Test
    void sobre_semRascunho_aindaDevolveTextoComDadosReais() {
        Ong ong = criarOngComNecessidade();

        SobreOngRequestDTO req = new SobreOngRequestDTO();
        req.setOngId(ong.getId());

        SobreOngResponseDTO resp = sobreOngService.gerar(req);

        assertFalse(resp.getDescricao().isBlank());
        assertTrue(resp.getDescricao().contains(ong.getNome()));
        assertEquals("regras", resp.getModo());
    }

    @Test
    void sobre_ajusteEncurtar_reduzOTexto() {
        Ong ong = criarOngComNecessidade();

        SobreOngRequestDTO longo = new SobreOngRequestDTO();
        longo.setOngId(ong.getId());
        longo.setRascunho("ajudamos criancas e idosos");
        String textoLongo = sobreOngService.gerar(longo).getDescricao();

        SobreOngRequestDTO curto = new SobreOngRequestDTO();
        curto.setOngId(ong.getId());
        curto.setRascunho("ajudamos criancas e idosos");
        curto.setAjuste("deixe mais curto");
        String textoCurto = sobreOngService.gerar(curto).getDescricao();

        assertTrue(textoCurto.length() <= textoLongo.length(),
                "com 'mais curto' o texto nao deveria crescer");
    }

    // ---------------------------------------------------------------
    // A descricao da ONG agora aceita ate 1000 chars (era 255). Um "Sobre"
    // longo (~600 chars, como o gerado pela IA) persiste sem estourar a coluna.
    // ---------------------------------------------------------------
    @Test
    void descricaoLonga_ate1000_persisteSemEstourar() {
        Ong ong = criarOngComNecessidade();

        // ~600 chars (folgado dentro do novo limite de 1000, alem do antigo 255).
        String frase = "A Casa do Sol e uma organizacao social que atende familias em "
                + "situacao de vulnerabilidade, oferecendo acolhimento, alimentacao e "
                + "apoio educacional para criancas e idosos da comunidade. ";
        String longa = frase.repeat(3).trim();
        assertTrue(longa.length() > 255 && longa.length() <= 1000,
                "o cenario precisa de um texto entre 256 e 1000 chars: " + longa.length());

        ong.setDescricao(longa);
        Ong salva = ongRepository.saveAndFlush(ong);

        Ong recarregada = ongRepository.findById(salva.getId()).orElseThrow();
        assertEquals(longa, recarregada.getDescricao(),
                "a descricao longa deveria persistir integra (coluna VARCHAR(1000))");
    }

    @Test
    void sobre_ongInexistente_devolveTextoGenerico_naoQuebra() {
        SobreOngRequestDTO req = new SobreOngRequestDTO();
        req.setOngId(9_999_999L);
        req.setRascunho("qualquer coisa");

        SobreOngResponseDTO resp = sobreOngService.gerar(req);

        assertFalse(resp.getDescricao().isBlank());
        assertEquals("regras", resp.getModo());
    }
}
