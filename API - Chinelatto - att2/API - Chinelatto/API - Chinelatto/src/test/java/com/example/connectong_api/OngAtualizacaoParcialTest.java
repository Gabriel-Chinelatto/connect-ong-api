package com.example.connectong_api;

import com.example.connectong_api.dto.OngUpdateDTO;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.security.UsuarioAutenticado;
import com.example.connectong_api.service.ONGService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Atualizacao do perfil da ONG (PUT /ongs/{id}).
 *
 * BUG REAL corrigido aqui (10/08/2026): o GET do perfil NAO devolve o e-mail
 * (privacidade), entao o painel da ONG carregava o campo vazio e o reenviava
 * como "" ao salvar. Como a entidade Ong tem @NotBlank @Email, a violacao
 * estourava no flush e virava **500** — na pratica a ONG nao conseguia salvar
 * NADA no seu perfil (nem telefone, nem cidade, nem descricao).
 *
 * Regra travada: campos obrigatorios so sao sobrescritos quando vem
 * PREENCHIDOS; os opcionais continuam podendo ser limpos de proposito.
 */
@SpringBootTest
class OngAtualizacaoParcialTest {

    @Autowired private ONGService ongService;
    @Autowired private ONGRepository ongRepository;

    private static final AtomicLong SEQ = new AtomicLong(1);

    /** Cria a ONG e ja autentica a sessao COMO ela (o service exige o dono). */
    private Ong novaOng() {
        long n = SEQ.getAndIncrement();
        Ong ong = ongRepository.save(new Ong(
                "Lar Teste " + n,
                "lar" + n + "@parcial.test",
                "1930000" + n,
                "Limeira",
                "Acolhe idosos."));
        autenticarComo(ong);
        return ong;
    }

    private void autenticarComo(Ong ong) {
        UsuarioAutenticado principal =
                new UsuarioAutenticado(ong.getId(), "ONG", ong.getId());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void limparAutenticacao() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emailVazio_naoApaga_eNaoQuebra() {
        Ong ong = novaOng();
        String emailOriginal = ong.getEmail();

        OngUpdateDTO dto = new OngUpdateDTO();
        dto.setNome(ong.getNome());
        dto.setEmail("");                       // <- exatamente o que o painel enviava
        dto.setTelefone("(19) 3441-1000");
        dto.setCidade("Limeira - SP");
        dto.setDescricao("Descricao nova salva pela ONG.");

        ResponseEntity<?> resp = ongService.atualizar(ong.getId(), dto);
        assertEquals(200, resp.getStatusCode().value(),
                "salvar com e-mail vazio precisa funcionar (antes dava 500)");

        Ong salva = ongRepository.findById(ong.getId()).orElseThrow();
        assertEquals(emailOriginal, salva.getEmail(), "o e-mail nao pode ser perdido");
        assertEquals("Limeira - SP", salva.getCidade(), "a cidade precisa ter sido salva");
        assertEquals("Descricao nova salva pela ONG.", salva.getDescricao());
        assertEquals("(19) 3441-1000", salva.getTelefone());
    }

    @Test
    void emailPreenchido_atualizaNormalmente() {
        Ong ong = novaOng();

        OngUpdateDTO dto = new OngUpdateDTO();
        dto.setNome(ong.getNome());
        dto.setEmail("novo" + ong.getId() + "@parcial.test");
        dto.setTelefone(ong.getTelefone());
        dto.setCidade(ong.getCidade());
        dto.setDescricao(ong.getDescricao());

        ongService.atualizar(ong.getId(), dto);

        Ong salva = ongRepository.findById(ong.getId()).orElseThrow();
        assertEquals("novo" + ong.getId() + "@parcial.test", salva.getEmail());
    }

    @Test
    void nomeVazio_naoApagaONome() {
        Ong ong = novaOng();
        String nomeOriginal = ong.getNome();

        OngUpdateDTO dto = new OngUpdateDTO();
        dto.setNome("   ");                     // so espacos
        dto.setTelefone(ong.getTelefone());
        dto.setCidade(ong.getCidade());
        dto.setDescricao(ong.getDescricao());

        ResponseEntity<?> resp = ongService.atualizar(ong.getId(), dto);
        assertEquals(200, resp.getStatusCode().value());

        Ong salva = ongRepository.findById(ong.getId()).orElseThrow();
        assertEquals(nomeOriginal, salva.getNome(), "o nome nao pode virar vazio");
    }

    @Test
    void camposOpcionais_podemSerLimposDePropostio() {
        Ong ong = novaOng();

        OngUpdateDTO dto = new OngUpdateDTO();
        dto.setNome(ong.getNome());
        dto.setTelefone("");                    // a ONG apagou o telefone
        dto.setCidade(ong.getCidade());
        dto.setDescricao(ong.getDescricao());

        ongService.atualizar(ong.getId(), dto);

        Ong salva = ongRepository.findById(ong.getId()).orElseThrow();
        assertEquals("", salva.getTelefone(), "limpar um campo opcional deve funcionar");
    }
}
