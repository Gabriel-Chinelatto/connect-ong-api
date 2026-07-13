package com.example.connectong_api.service;

import com.example.connectong_api.dto.FreteRequestDTO;
import com.example.connectong_api.dto.FreteResponseDTO;
import com.example.connectong_api.dto.FreteResponseDTO.Modalidade;
import com.example.connectong_api.util.Categorias;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Frete ESTIMADO (nao e cotacao oficial) entre duas cidades, para ajudar doador e
 * ONG a combinar o envio de uma doacao. Combina:
 *   - PESO: informado (pesoKg > 0) ou estimado do item/quantidade (ItemIaService);
 *   - DISTANCIA: base offline de municipios (GeoService, Haversine); sem a cidade
 *     na base, usa uma distancia padrao e marca um aviso de aproximacao;
 *   - MODALIDADES: formulas simples parametrizadas em application.properties
 *     (prefixo app.frete.*), com "Entrega combinada" gratis quando e perto.
 *
 * Nunca lanca por falta de dados: sempre devolve pelo menos uma modalidade paga.
 * Valores arredondados a 2 casas e nunca negativos (piso 0).
 */
@Service
public class FreteService {

    @Autowired private GeoService geoService;
    @Autowired private ItemIaService itemIaService;

    @Value("${app.frete.correios.base:15.0}")
    private double correiosBase;
    @Value("${app.frete.correios.porKg:1.2}")
    private double correiosPorKg;
    @Value("${app.frete.correios.porKm:0.09}")
    private double correiosPorKm;

    @Value("${app.frete.transportadora.base:20.0}")
    private double transpBase;
    @Value("${app.frete.transportadora.porKg:0.7}")
    private double transpPorKg;
    @Value("${app.frete.transportadora.porKm:0.06}")
    private double transpPorKm;

    // Ate este raio (km) consideramos "mesma cidade/regiao" -> oferece entrega
    // combinada (retirada/entrega no chat, gratis).
    @Value("${app.frete.local.raioKm:30}")
    private int raioLocalKm;

    // Distancia usada quando uma das cidades nao esta na base offline.
    @Value("${app.frete.distanciaPadraoKm:300}")
    private int distanciaPadraoKm;

    private static final String AVISO =
            "Valores estimados por distância e peso — não são cotação oficial.";

    public FreteResponseDTO estimar(FreteRequestDTO req) {
        FreteResponseDTO resp = new FreteResponseDTO();
        resp.setAviso(AVISO);

        // ---- PESO + categoria + resumo + modo ----
        double peso;
        boolean pesoEstimado;
        String categoria;
        String itemResumo;
        String modo;

        if (req.getPesoKg() != null && req.getPesoKg() > 0) {
            peso = arredondar(req.getPesoKg());
            pesoEstimado = false;
            // Peso veio pronto: categoria do request (se houver), resumo do item.
            categoria = temTexto(req.getCategoria())
                    ? Categorias.normalizar(req.getCategoria().trim()) : null;
            itemResumo = temTexto(req.getItem()) ? req.getItem().trim() : null;
            modo = "regras";
        } else {
            // Passa a categoria ESCOLHIDA pelo usuario (se houver) como dica: o
            // ItemIaService a honra na resposta e a usa para calibrar o peso.
            String categoriaHint = temTexto(req.getCategoria()) ? req.getCategoria().trim() : null;
            ItemIaService.ItemInfo info =
                    itemIaService.estimar(req.getItem(), req.getQuantidade(), categoriaHint);
            peso = arredondar(info.pesoKg());
            pesoEstimado = true;
            // Categoria final = a escolhida pelo usuario; senao a deduzida pelo item.
            categoria = temTexto(req.getCategoria())
                    ? Categorias.normalizar(req.getCategoria().trim()) : info.categoria();
            itemResumo = info.resumo();
            modo = info.modo();
        }
        resp.setPesoKg(peso);
        resp.setPesoEstimado(pesoEstimado);
        resp.setCategoria(categoria);
        resp.setItemResumo(itemResumo);
        resp.setModo(modo);

        // ---- DISTANCIA ----
        Optional<Double> distOpt = geoService.distanciaKm(
                req.getOrigemCidade(), req.getOrigemUf(),
                req.getDestinoCidade(), req.getDestinoUf());
        int distancia;
        if (distOpt.isPresent()) {
            distancia = (int) Math.round(distOpt.get());
        } else {
            distancia = distanciaPadraoKm;
            resp.setAviso(AVISO + " A distância é aproximada (cidade não encontrada na base).");
        }
        resp.setDistanciaKm(distancia);

        // ---- ORIGEM / DESTINO (rotulos) ----
        resp.setOrigem(rotulo(req.getOrigemCidade(), req.getOrigemUf()));
        resp.setDestino(rotulo(req.getDestinoCidade(), req.getDestinoUf()));

        // ---- MODALIDADES ----
        resp.setModalidades(calcularModalidades(peso, distancia));
        return resp;
    }

    private List<Modalidade> calcularModalidades(double peso, int distancia) {
        List<Modalidade> lista = new ArrayList<>();

        // Correios (estimado) — PAC.
        double vCorreios = correiosBase + correiosPorKg * peso + correiosPorKm * distancia;
        int pCorreios = (int) Math.ceil(distancia / 250.0) + 2;
        lista.add(new Modalidade("Correios (estimado)", arredondarNaoNegativo(vCorreios),
                pCorreios, "PAC estimado"));

        // Transportadora (estimado) — carga fracionada.
        double vTransp = transpBase + transpPorKg * peso + transpPorKm * distancia;
        int pTransp = (int) Math.ceil(distancia / 400.0) + 2;
        lista.add(new Modalidade("Transportadora (estimado)", arredondarNaoNegativo(vTransp),
                pTransp, "carga fracionada"));

        // Entrega combinada — so quando e perto (mesma cidade/regiao).
        if (distancia <= raioLocalKm) {
            lista.add(new Modalidade("Entrega combinada", 0.0, 0,
                    "combine a retirada/entrega no chat"));
        }
        return lista;
    }

    // Rotulo "Cidade/UF" (UF so quando informada).
    private String rotulo(String cidade, String uf) {
        String c = cidade == null ? "" : cidade.trim();
        if (temTexto(uf)) return c + "/" + uf.trim().toUpperCase();
        return c;
    }

    private double arredondar(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // Arredonda a 2 casas com piso 0 (frete nunca negativo).
    private double arredondarNaoNegativo(double v) {
        double r = arredondar(v);
        return r < 0 ? 0.0 : r;
    }

    private boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
