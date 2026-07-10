package com.example.connectong_api;

import com.example.connectong_api.service.ItemIaService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes do ItemIaService no modo REGRAS (sem chave de IA na CI): estima peso e
 * categoria a partir da descricao + quantidade, sempre devolvendo algo (piso 1kg).
 */
@SpringBootTest
class ItemIaRegrasTest {

    @Autowired private ItemIaService itemIaService;

    @Test
    void cobertores_comQuantidade_multiplicaPeso() {
        ItemIaService.ItemInfo info = itemIaService.estimar("cobertores", 10);
        // 10 * 1.5 kg = 15 kg
        assertEquals(15.0, info.pesoKg());
        assertEquals("Roupas", info.categoria());
        assertEquals("regras", info.modo());
        assertNotNull(info.resumo());
    }

    @Test
    void cestaBasica_semQuantidade_pesoUnitarioAlto() {
        ItemIaService.ItemInfo info = itemIaService.estimar("cesta basica", null);
        assertEquals(12.0, info.pesoKg());
        assertEquals("Alimentos", info.categoria());
        assertEquals("regras", info.modo());
    }

    @Test
    void itemDesconhecido_temPisoDe1kg_categoriaOutros() {
        ItemIaService.ItemInfo info = itemIaService.estimar("uma coisa qualquer", null);
        assertTrue(info.pesoKg() >= 1.0, "piso de 1 kg");
        assertEquals("Outros", info.categoria());
        assertEquals("regras", info.modo());
    }

    @Test
    void quantidadeExtraidaDoTexto_quandoNaoInformada() {
        // "5 livros" -> extrai 5, livro = 0.5 kg -> 2.5 kg
        ItemIaService.ItemInfo info = itemIaService.estimar("5 livros", null);
        assertEquals(2.5, info.pesoKg());
        assertEquals("Educacao", info.categoria());
    }

    @Test
    void textoVazio_naoQuebra() {
        ItemIaService.ItemInfo info = itemIaService.estimar("", null);
        assertTrue(info.pesoKg() >= 1.0);
        assertNotNull(info.categoria());
        assertEquals("regras", info.modo());
    }
}
