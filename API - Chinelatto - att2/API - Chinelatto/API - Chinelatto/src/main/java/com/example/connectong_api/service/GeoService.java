package com.example.connectong_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base OFFLINE de coordenadas de municipios brasileiros, so para calcular
 * DISTANCIA entre cidades (o Connect ONG nao guarda lat/long em nenhuma entidade).
 *
 * Fonte dos dados (baixada UMA vez para src/main/resources/geo/):
 *   https://github.com/kelvins/municipios-brasileiros (dominio publico).
 *   - municipios.json: objetos {nome, latitude, longitude, capital(0/1), codigo_uf}
 *   - estados.json:    objetos {codigo_uf, uf (sigla), nome}
 * Se um dia os arquivos faltarem no classpath, o servico sobe vazio e todos os
 * metodos devolvem Optional.empty() (o chamador degrada para distancia padrao),
 * sem quebrar a aplicacao.
 *
 * Indice em memoria: nomeNormalizado (minusculo, sem acento) -> lista de cidades
 * homonimas (mesma cidade em estados diferentes). Em colisao, {@link #coordenadas}
 * prefere a que casa a UF informada; sem UF, a CAPITAL; senao a primeira.
 */
@Service
public class GeoService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Uma cidade da base: coordenadas + UF (sigla) + se e capital. */
    private static class Cidade {
        final double lat;
        final double lon;
        final String uf;
        final boolean capital;

        Cidade(double lat, double lon, String uf, boolean capital) {
            this.lat = lat;
            this.lon = lon;
            this.uf = uf;
            this.capital = capital;
        }
    }

    // nomeNormalizado -> cidades homonimas.
    private final Map<String, List<Cidade>> indice = new LinkedHashMap<>();

    @PostConstruct
    public void carregar() {
        try {
            // codigo_uf -> sigla (ex.: 35 -> "SP"), a partir de estados.json.
            Map<Integer, String> ufPorCodigo = carregarEstados();
            carregarMunicipios(ufPorCodigo);
        } catch (Exception e) {
            // Base indisponivel: segue vazia (distancia cai no fallback do chamador).
            indice.clear();
        }
    }

    private Map<Integer, String> carregarEstados() throws Exception {
        Map<Integer, String> ufPorCodigo = new LinkedHashMap<>();
        ClassPathResource recurso = new ClassPathResource("geo/estados.json");
        if (!recurso.exists()) return ufPorCodigo;
        try (InputStream is = recurso.getInputStream()) {
            JsonNode arr = objectMapper.readTree(is);
            if (arr.isArray()) {
                for (JsonNode e : arr) {
                    int codigo = e.path("codigo_uf").asInt(-1);
                    String uf = e.path("uf").asText("").trim().toUpperCase();
                    if (codigo >= 0 && !uf.isEmpty()) ufPorCodigo.put(codigo, uf);
                }
            }
        }
        return ufPorCodigo;
    }

    private void carregarMunicipios(Map<Integer, String> ufPorCodigo) throws Exception {
        ClassPathResource recurso = new ClassPathResource("geo/municipios.json");
        if (!recurso.exists()) return;
        try (InputStream is = recurso.getInputStream()) {
            JsonNode arr = objectMapper.readTree(is);
            if (!arr.isArray()) return;
            for (JsonNode m : arr) {
                String nome = m.path("nome").asText("").trim();
                if (nome.isEmpty()) continue;
                double lat = m.path("latitude").asDouble(Double.NaN);
                double lon = m.path("longitude").asDouble(Double.NaN);
                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;
                int codigoUf = m.path("codigo_uf").asInt(-1);
                String uf = ufPorCodigo.getOrDefault(codigoUf, "");
                boolean capital = m.path("capital").asInt(0) == 1;
                indice.computeIfAbsent(normalizar(nome), k -> new ArrayList<>())
                        .add(new Cidade(lat, lon, uf, capital));
            }
        }
    }

    /** Quantas cidades a base carregou (diagnostico/testes). 0 = base ausente. */
    public int totalCidades() {
        int total = 0;
        for (List<Cidade> l : indice.values()) total += l.size();
        return total;
    }

    /**
     * Coordenadas [latitude, longitude] de uma cidade. Em colisao de nome entre
     * estados: prefere a que casa `uf` (se informado), senao a CAPITAL, senao a
     * primeira. Retorna vazio quando a cidade nao esta na base.
     */
    public Optional<double[]> coordenadas(String cidade, String uf) {
        if (cidade == null || cidade.isBlank()) return Optional.empty();
        List<Cidade> candidatas = indice.get(normalizar(cidade));
        if (candidatas == null || candidatas.isEmpty()) return Optional.empty();

        String ufNorm = uf == null ? "" : uf.trim().toUpperCase();

        // 1) casa a UF informada.
        if (!ufNorm.isEmpty()) {
            for (Cidade c : candidatas) {
                if (ufNorm.equals(c.uf)) return Optional.of(new double[]{c.lat, c.lon});
            }
        }
        // 2) sem UF (ou UF nao casou): prefere a capital.
        for (Cidade c : candidatas) {
            if (c.capital) return Optional.of(new double[]{c.lat, c.lon});
        }
        // 3) primeira da lista.
        Cidade c = candidatas.get(0);
        return Optional.of(new double[]{c.lat, c.lon});
    }

    /**
     * Distancia em KM (inteiro, arredondado) entre duas cidades, pela formula de
     * Haversine (raio da Terra = 6371 km). Vazio se uma das cidades nao estiver
     * na base offline.
     */
    public Optional<Double> distanciaKm(String cidadeOrigem, String ufOrigem,
                                        String cidadeDestino, String ufDestino) {
        Optional<double[]> a = coordenadas(cidadeOrigem, ufOrigem);
        Optional<double[]> b = coordenadas(cidadeDestino, ufDestino);
        if (a.isEmpty() || b.isEmpty()) return Optional.empty();
        double km = haversine(a.get()[0], a.get()[1], b.get()[0], b.get()[1]);
        return Optional.of((double) Math.round(km));
    }

    // Haversine: distancia de grande circulo entre dois pontos (lat/lon em graus).
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0; // raio medio da Terra em km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // Minusculas + sem acento (mesma tecnica do AssistenteService/Categorias).
    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase().trim();
    }
}
