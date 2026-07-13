package com.example.connectong_api;

import com.example.connectong_api.dto.FreteRequestDTO;
import com.example.connectong_api.dto.FreteResponseDTO;
import com.example.connectong_api.service.FreteService;
import com.example.connectong_api.service.GeoService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes da estimativa de frete (FreteService) e da base offline de distancias
 * (GeoService). Rodam com H2, sem chave de IA (peso informado => modo "regras").
 */
@SpringBootTest
class FreteEstimativaTest {

    @Autowired private FreteService freteService;
    @Autowired private GeoService geoService;

    // ---------------------------------------------------------------
    // GeoService: base offline carregou e a distancia e plausivel.
    // ---------------------------------------------------------------
    @Test
    void geoService_carregouBase_eCalculaDistanciaPlausivel() {
        assertTrue(geoService.totalCidades() > 1000,
                "a base offline de municipios deveria ter carregado (>1000 cidades)");

        Optional<Double> dist = geoService.distanciaKm("Limeira", "SP", "Rio de Janeiro", "RJ");
        assertTrue(dist.isPresent(), "deveria achar Limeira e Rio de Janeiro na base");
        double km = dist.get();
        // Limeira/SP -> Rio/RJ e ~370-430 km em linha reta.
        assertTrue(km > 300 && km < 500, "distancia fora do esperado: " + km);
    }

    @Test
    void geoService_cidadeInexistente_retornaVazio() {
        assertTrue(geoService.distanciaKm("Cidade Que Nao Existe XYZ", null,
                "Rio de Janeiro", "RJ").isEmpty());
    }

    // ---------------------------------------------------------------
    // FreteService: Limeira -> Rio com peso informado -> 2 modalidades pagas (>0),
    // distancia plausivel, peso NAO estimado, modo "regras".
    // ---------------------------------------------------------------
    @Test
    void frete_limeiraParaRio_comPesoInformado_modalidadesComValor() {
        FreteRequestDTO req = new FreteRequestDTO();
        req.setOrigemCidade("Limeira");
        req.setOrigemUf("SP");
        req.setDestinoCidade("Rio de Janeiro");
        req.setDestinoUf("RJ");
        req.setPesoKg(15.0);

        FreteResponseDTO resp = freteService.estimar(req);

        assertEquals(15.0, resp.getPesoKg());
        assertFalse(resp.isPesoEstimado(), "peso foi informado, nao estimado");
        assertNotNull(resp.getDistanciaKm());
        assertTrue(resp.getDistanciaKm() > 300 && resp.getDistanciaKm() < 500,
                "distancia fora do esperado: " + resp.getDistanciaKm());

        // Distancia > 30 km => sem "Entrega combinada" => exatamente 2 modalidades pagas.
        assertEquals(2, resp.getModalidades().size());
        for (FreteResponseDTO.Modalidade m : resp.getModalidades()) {
            assertTrue(m.getValor() > 0, "modalidade " + m.getNome() + " deveria ter valor > 0");
            assertTrue(m.getPrazoDias() > 0, "prazo deveria ser positivo");
        }
        assertEquals("regras", resp.getModo());
        assertNotNull(resp.getAviso());
    }

    // ---------------------------------------------------------------
    // Mesma cidade (distancia <= raio local) -> inclui "Entrega combinada" gratis.
    // ---------------------------------------------------------------
    @Test
    void frete_mesmaCidade_incluiEntregaCombinadaGratis() {
        FreteRequestDTO req = new FreteRequestDTO();
        req.setOrigemCidade("Campinas");
        req.setOrigemUf("SP");
        req.setDestinoCidade("Campinas");
        req.setDestinoUf("SP");
        req.setPesoKg(5.0);

        FreteResponseDTO resp = freteService.estimar(req);

        assertEquals(0, resp.getDistanciaKm());
        assertEquals(3, resp.getModalidades().size(), "mesma cidade deveria ter 3 modalidades");
        boolean temCombinada = resp.getModalidades().stream()
                .anyMatch(m -> "Entrega combinada".equals(m.getNome()) && m.getValor() == 0.0);
        assertTrue(temCombinada, "deveria ter Entrega combinada gratis");
    }

    // ---------------------------------------------------------------
    // Cidade fora da base -> distancia padrao (300) + aviso de aproximacao.
    // ---------------------------------------------------------------
    @Test
    void frete_cidadeForaDaBase_usaDistanciaPadraoEAvisa() {
        FreteRequestDTO req = new FreteRequestDTO();
        req.setOrigemCidade("Xpto Inexistente 123");
        req.setDestinoCidade("Rio de Janeiro");
        req.setDestinoUf("RJ");
        req.setPesoKg(2.0);

        FreteResponseDTO resp = freteService.estimar(req);

        assertEquals(300, resp.getDistanciaKm());
        assertTrue(resp.getAviso().toLowerCase().contains("aproximada"));
        assertTrue(resp.getModalidades().size() >= 2);
    }

    // ---------------------------------------------------------------
    // Sem peso informado -> estima do item (modo "regras", pesoEstimado=true).
    // ---------------------------------------------------------------
    @Test
    void frete_semPeso_estimaDoItem() {
        FreteRequestDTO req = new FreteRequestDTO();
        req.setOrigemCidade("Limeira");
        req.setOrigemUf("SP");
        req.setDestinoCidade("Sao Paulo");
        req.setDestinoUf("SP");
        req.setItem("10 cobertores");
        req.setQuantidade(10);

        FreteResponseDTO resp = freteService.estimar(req);

        assertTrue(resp.isPesoEstimado());
        // 10 cobertores * 1.5 kg = 15 kg.
        assertEquals(15.0, resp.getPesoKg());
        assertEquals("Roupas", resp.getCategoria());
        assertEquals("regras", resp.getModo());
    }

    // ---------------------------------------------------------------
    // A categoria ESCOLHIDA pelo usuario e honrada, mesmo quando o item
    // ("morango") nao casa nenhuma palavra-chave (que daria "Outros").
    // Peso ~100kg vem do peso INFORMADO; a categoria continua a escolhida.
    // ---------------------------------------------------------------
    @Test
    void frete_categoriaEscolhida_ehHonrada_comPesoInformado() {
        FreteRequestDTO req = new FreteRequestDTO();
        req.setOrigemCidade("Limeira");
        req.setOrigemUf("SP");
        req.setDestinoCidade("Rio de Janeiro");
        req.setDestinoUf("RJ");
        req.setItem("10 caixas de 10 kg de morango");
        req.setQuantidade(10);
        req.setCategoria("Alimentos");
        req.setPesoKg(100.0);

        FreteResponseDTO resp = freteService.estimar(req);

        assertEquals(100.0, resp.getPesoKg(), "peso informado deve ser refletido (~100kg)");
        assertFalse(resp.isPesoEstimado());
        assertEquals("Alimentos", resp.getCategoria(),
                "categoria escolhida deve ser honrada, nao deduzida do item");
    }

    // ---------------------------------------------------------------
    // Sem peso informado: a categoria escolhida ("Alimentos") e honrada
    // mesmo que o item ("morango") sozinho seria classificado como "Outros".
    // ---------------------------------------------------------------
    @Test
    void frete_categoriaEscolhida_ehHonrada_comPesoEstimado() {
        FreteRequestDTO req = new FreteRequestDTO();
        req.setOrigemCidade("Limeira");
        req.setOrigemUf("SP");
        req.setDestinoCidade("Sao Paulo");
        req.setDestinoUf("SP");
        req.setItem("morango");
        req.setQuantidade(5);
        req.setCategoria("Alimentos");

        FreteResponseDTO resp = freteService.estimar(req);

        assertTrue(resp.isPesoEstimado());
        assertEquals("Alimentos", resp.getCategoria());
        assertTrue(resp.getPesoKg() > 0);
        assertEquals("regras", resp.getModo());
    }
}
