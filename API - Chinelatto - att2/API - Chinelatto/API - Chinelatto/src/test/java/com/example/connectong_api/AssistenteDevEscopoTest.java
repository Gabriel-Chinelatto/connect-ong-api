package com.example.connectong_api;

import com.example.connectong_api.dto.AssistenteRequestDTO;
import com.example.connectong_api.dto.AssistenteResponseDTO;
import com.example.connectong_api.service.AssistenteDevService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Assistente "Sobre o Desenvolvimento" no modo REGRAS (a CI nao tem chave de
 * IA, entao estes testes exercitam exatamente o fallback).
 *
 * Feedback de 10/08/2026: o usuario pediu que o assistente responda QUALQUER
 * pergunta sobre o projeto e RECUSE o que nao for do projeto. Na epoca,
 * perguntar "qual seu time de futebol favorito?" devolvia o historico de
 * versoes — a palavra "favorito" casava com a secao de "favoritos" e o
 * assistente despejava a secao inteira.
 */
@SpringBootTest
class AssistenteDevEscopoTest {

    @Autowired private AssistenteDevService service;

    private String responder(String pergunta) {
        AssistenteRequestDTO dto = new AssistenteRequestDTO();
        dto.setMensagem(pergunta);
        ResponseEntity<?> resp = service.responder(dto);
        assertEquals(200, resp.getStatusCode().value());
        assertInstanceOf(AssistenteResponseDTO.class, resp.getBody());
        return ((AssistenteResponseDTO) resp.getBody()).getResposta();
    }

    @Test
    void perguntaForaDoEscopo_recebeRecusaEducada() {
        String r = responder("qual seu time de futebol favorito?");

        assertTrue(r.contains("só falo sobre como o Connect ONG foi desenvolvido"),
                "deveria recusar o assunto; veio: " + r);
        assertFalse(r.contains("Histórico de versões"),
                "nao pode despejar uma secao do documento numa pergunta fora de escopo");
    }

    @Test
    void outroAssuntoQualquer_tambemERecusado() {
        String r = responder("me da uma receita de bolo de cenoura");
        assertTrue(r.contains("só falo sobre como o Connect ONG foi desenvolvido"),
                "veio: " + r);
    }

    @Test
    void perguntaSobreOProjeto_recebeConteudoDoDocumento() {
        String r = responder("como funciona o match entre doador e ONG?");

        assertFalse(r.contains("só falo sobre como o Connect ONG foi desenvolvido"),
                "pergunta legitima nao pode ser recusada; veio: " + r);
        assertTrue(r.toUpperCase().contains("PENDENTE") || r.toLowerCase().contains("match"),
                "deveria explicar o match; veio: " + r);
    }

    @Test
    void perguntaTecnicaSobreAApi_encontraOConteudo() {
        String r = responder("como a api faz a autorizacao por dono dos dados?");

        assertFalse(r.contains("só falo sobre como o Connect ONG foi desenvolvido"),
                "pergunta legitima nao pode ser recusada; veio: " + r);
    }

    @Test
    void perguntaSobreBancoDeDados_encontraOConteudo() {
        String r = responder("qual banco de dados e como sao feitas as migracoes?");

        assertFalse(r.contains("só falo sobre como o Connect ONG foi desenvolvido"),
                "pergunta legitima nao pode ser recusada; veio: " + r);
    }
}
