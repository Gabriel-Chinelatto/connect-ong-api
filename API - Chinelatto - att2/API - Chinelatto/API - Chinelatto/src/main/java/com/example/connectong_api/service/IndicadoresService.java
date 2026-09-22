package com.example.connectong_api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * Indicadores de resultado da plataforma (item F-08 do Plano de Acao — nota 5
 * em "Analise de Dados e Resultados" na FECITEC). Antes so havia CONTAGENS
 * soltas (quantas ONGs, quantos doadores); agora da para responder "a
 * plataforma funciona?":
 *
 * <ul>
 *   <li><b>Funil</b>: necessidade publicada → recebeu interesse → aceito →
 *       doacao concluida;</li>
 *   <li><b>Taxa de atendimento</b>: % das necessidades com ao menos uma doacao
 *       concluida;</li>
 *   <li><b>Tempo de resposta</b>: mediana de dias entre o doador demonstrar
 *       interesse e a ONG responder, e ate a doacao ser concluida;</li>
 *   <li>recortes <b>por categoria</b>, <b>por estado</b> e <b>mes a mes</b>
 *       (ultimos 12 meses).</li>
 * </ul>
 *
 * <p><b>Desempenho:</b> com a API longe do banco, cada ida ao MySQL custa
 * ~600 ms (ver EstatisticasService). Por isso todas as agregacoes saem em UMA
 * consulta ({@code UNION ALL}, uma linha por (tipo, chave, chave2, valor)) e os
 * tempos em uma segunda. A versao publica ainda fica 10 minutos em cache.
 *
 * <p>SQL portavel entre MySQL (producao) e H2 (testes): so COUNT/SUM, YEAR,
 * MONTH e CONCAT. O GROUP BY repete a MESMA expressao do SELECT: agrupar por
 * YEAR(x), MONTH(x) e selecionar CONCAT(...) e recusado pelo H2 e pelo MySQL
 * com ONLY_FULL_GROUP_BY (e so estoura quando ha linhas no mes).
 */
@Service
public class IndicadoresService {

    private static final Duration CACHE = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private volatile Map<String, Object> cachePublico;
    private volatile LocalDateTime cacheGeradoEm;

    public IndicadoresService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Indicadores de toda a plataforma (ONGs ativas), com cache. */
    public Map<String, Object> publicos() {
        LocalDateTime agora = LocalDateTime.now();
        if (cachePublico == null || cacheGeradoEm.plus(CACHE).isBefore(agora)) {
            cachePublico = calcular(null);
            cacheGeradoEm = agora;
        }
        return cachePublico;
    }

    /** Indicadores de UMA ONG (painel da ONG), sempre frescos. */
    public Map<String, Object> daOng(Long ongId) {
        return calcular(ongId);
    }

    public void limparCache() {
        cachePublico = null;
    }

    // ------------------------------------------------------------------

    Map<String, Object> calcular(Long ongId) {
        String filtroN = ongId == null ? "" : " AND n.ong_id = ?";
        String filtroD = ongId == null ? "" : " AND d.ong_id = ?";
        String ongAtiva = " JOIN ong o ON o.id = n.ong_id AND o.data_exclusao IS NULL";
        String mes = "CONCAT(YEAR(%1$s), '-', MONTH(%1$s))";
        LocalDateTime desde = YearMonth.now().minusMonths(11).atDay(1).atStartOfDay();

        // Cada parte devolve (tipo, k1, k2, v).
        List<String> partes = List.of(
                "SELECT 'nec_cat' AS t, n.categoria AS k1, n.status AS k2, COUNT(*) AS v FROM necessidade n"
                        + ongAtiva + " WHERE 1=1" + filtroN + " GROUP BY n.categoria, n.status",
                "SELECT 'int_cat', n.categoria, i.status, COUNT(*) FROM interesse i JOIN necessidade n ON n.id = i.necessidade_id"
                        + ongAtiva + " WHERE 1=1" + filtroN + " GROUP BY n.categoria, i.status",
                "SELECT 'nec_com_int', n.categoria, NULL, COUNT(DISTINCT n.id) FROM interesse i JOIN necessidade n ON n.id = i.necessidade_id"
                        + ongAtiva + " WHERE 1=1" + filtroN + " GROUP BY n.categoria",
                "SELECT 'nec_atendida', n.categoria, NULL, COUNT(DISTINCT n.id) FROM interesse i JOIN necessidade n ON n.id = i.necessidade_id"
                        + ongAtiva + " WHERE i.status = 'CONCLUIDO'" + filtroN + " GROUP BY n.categoria",
                "SELECT 'nec_cidade', o.cidade, NULL, COUNT(*) FROM necessidade n"
                        + ongAtiva + " WHERE 1=1" + filtroN + " GROUP BY o.cidade",
                "SELECT 'mes_nec', " + String.format(mes, "n.data_criacao") + ", NULL, COUNT(*) FROM necessidade n"
                        + ongAtiva + " WHERE n.data_criacao >= ?" + filtroN
                        + " GROUP BY " + String.format(mes, "n.data_criacao"),
                "SELECT 'mes_int', " + String.format(mes, "i.data_criacao") + ", NULL, COUNT(*) FROM interesse i JOIN necessidade n ON n.id = i.necessidade_id"
                        + ongAtiva + " WHERE i.data_criacao >= ?" + filtroN
                        + " GROUP BY " + String.format(mes, "i.data_criacao"),
                "SELECT 'mes_conc', " + String.format(mes, "i.data_conclusao") + ", NULL, COUNT(*) FROM interesse i JOIN necessidade n ON n.id = i.necessidade_id"
                        + ongAtiva + " WHERE i.status = 'CONCLUIDO' AND i.data_conclusao >= ?" + filtroN
                        + " GROUP BY " + String.format(mes, "i.data_conclusao"),
                "SELECT 'mes_pix', " + String.format(mes, "d.data_criacao") + ", NULL, SUM(d.valor) FROM doacao_financeira d"
                        + " WHERE d.data_criacao >= ?" + filtroD
                        + " GROUP BY " + String.format(mes, "d.data_criacao"));

        List<Object> params = new ArrayList<>();
        for (String p : partes) {
            if (p.contains(">= ?")) params.add(desde);
            if (ongId != null) params.add(ongId);
        }
        List<Map<String, Object>> linhas = jdbc.queryForList(String.join(" UNION ALL ", partes), params.toArray());

        // ---- agrega em memoria ----
        Map<String, long[]> cat = new TreeMap<>(); // categoria -> [necessidades, comInteresse, atendidas, interesses, aceitos, concluidos]
        long necessidades = 0, abertas = 0, interesses = 0, pendentes = 0, aceitos = 0, concluidos = 0, recusados = 0;
        long comInteresse = 0, atendidas = 0;
        Map<String, long[]> porUf = new TreeMap<>(); // uf -> [necessidades]
        Map<String, double[]> meses = new LinkedHashMap<>(); // "2026-9" -> [nec, int, conc, pix]
        for (int k = 11; k >= 0; k--) {
            YearMonth ym = YearMonth.now().minusMonths(k);
            meses.put(ym.getYear() + "-" + ym.getMonthValue(), new double[4]);
        }

        for (Map<String, Object> l : linhas) {
            String t = texto(l.get("t"));
            String k1 = texto(l.get("k1"));
            String k2 = texto(l.get("k2"));
            double v = l.get("v") == null ? 0 : ((Number) l.get("v")).doubleValue();
            long n = Math.round(v);
            String categoria = k1 == null || k1.isBlank() ? "Outros" : k1;
            switch (t) {
                case "nec_cat" -> {
                    necessidades += n;
                    if ("ABERTA".equals(k2)) abertas += n;
                    linha(cat, categoria)[0] += n;
                }
                case "int_cat" -> {
                    interesses += n;
                    linha(cat, categoria)[3] += n;
                    switch (k2 == null ? "" : k2) {
                        case "PENDENTE" -> pendentes += n;
                        case "ACEITO" -> { aceitos += n; linha(cat, categoria)[4] += n; }
                        case "CONCLUIDO" -> { aceitos += n; concluidos += n; linha(cat, categoria)[4] += n; linha(cat, categoria)[5] += n; }
                        case "RECUSADO" -> recusados += n;
                        default -> { }
                    }
                }
                case "nec_com_int" -> { comInteresse += n; linha(cat, categoria)[1] += n; }
                case "nec_atendida" -> { atendidas += n; linha(cat, categoria)[2] += n; }
                case "nec_cidade" -> porUf.computeIfAbsent(uf(k1), x -> new long[1])[0] += n;
                case "mes_nec", "mes_int", "mes_conc", "mes_pix" -> {
                    double[] m = meses.get(k1);
                    if (m != null) {
                        int idx = switch (t) { case "mes_nec" -> 0; case "mes_int" -> 1; case "mes_conc" -> 2; default -> 3; };
                        m[idx] += v;
                    }
                }
                default -> { }
            }
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("escopo", ongId == null ? "PLATAFORMA" : "ONG");
        if (ongId != null) r.put("ongId", ongId);
        r.put("geradoEm", LocalDateTime.now());

        Map<String, Object> funil = new LinkedHashMap<>();
        funil.put("necessidades", necessidades);
        funil.put("necessidadesComInteresse", comInteresse);
        funil.put("necessidadesAtendidas", atendidas);
        funil.put("necessidadesAbertas", abertas);
        funil.put("interesses", interesses);
        funil.put("interessesPendentes", pendentes);
        funil.put("interessesAceitos", aceitos);      // inclui os ja concluidos
        funil.put("interessesConcluidos", concluidos);
        funil.put("interessesRecusados", recusados);
        r.put("funil", funil);

        r.put("taxaAtendimento", pct(atendidas, necessidades));
        r.put("taxaEngajamento", pct(comInteresse, necessidades));
        r.put("taxaAceite", pct(aceitos, aceitos + recusados));
        r.putAll(tempos(ongId));

        List<Map<String, Object>> categorias = new ArrayList<>();
        cat.forEach((nome, c) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("categoria", nome);
            m.put("necessidades", c[0]);
            m.put("atendidas", c[2]);
            m.put("taxaAtendimento", pct(c[2], c[0]));
            m.put("interesses", c[3]);
            m.put("concluidos", c[5]);
            categorias.add(m);
        });
        categorias.sort((a, b) -> Long.compare((long) b.get("necessidades"), (long) a.get("necessidades")));
        r.put("porCategoria", categorias);

        List<Map<String, Object>> estados = new ArrayList<>();
        porUf.forEach((uf, c) -> estados.add(Map.of("uf", uf, "necessidades", c[0])));
        estados.sort((a, b) -> Long.compare((long) b.get("necessidades"), (long) a.get("necessidades")));
        r.put("porEstado", estados);

        List<Map<String, Object>> mensal = new ArrayList<>();
        meses.forEach((chave, m) -> {
            String[] am = chave.split("-");
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("mes", String.format("%s-%02d", am[0], Integer.parseInt(am[1])));
            x.put("necessidades", Math.round(m[0]));
            x.put("interesses", Math.round(m[1]));
            x.put("concluidos", Math.round(m[2]));
            x.put("valorPix", Math.round(m[3] * 100) / 100.0);
            mensal.add(x);
        });
        r.put("mensal", mensal);
        return r;
    }

    /** Medianas de tempo (em dias). Mediana e nao media: uma doacao esquecida por 200 dias nao distorce o numero. */
    private Map<String, Object> tempos(Long ongId) {
        String sql = "SELECT i.status, i.data_criacao, i.data_status, i.data_conclusao FROM interesse i"
                + " JOIN necessidade n ON n.id = i.necessidade_id"
                + " JOIN ong o ON o.id = n.ong_id AND o.data_exclusao IS NULL"
                + " WHERE i.status IN ('ACEITO', 'RECUSADO', 'CONCLUIDO')"
                + (ongId == null ? "" : " AND n.ong_id = ?");
        List<Map<String, Object>> linhas = ongId == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, ongId);
        List<Double> resposta = new ArrayList<>();
        List<Double> conclusao = new ArrayList<>();
        for (Map<String, Object> l : linhas) {
            LocalDateTime criado = data(l.get("data_criacao"));
            if (criado == null) continue;
            String status = texto(l.get("status"));
            LocalDateTime st = data(l.get("data_status"));
            LocalDateTime conc = data(l.get("data_conclusao"));
            // data_status marca a ULTIMA mudanca: para ACEITO/RECUSADO e a resposta da ONG.
            if (!"CONCLUIDO".equals(status) && st != null && !st.isBefore(criado)) {
                resposta.add(dias(criado, st));
            }
            if ("CONCLUIDO".equals(status) && conc != null && !conc.isBefore(criado)) {
                conclusao.add(dias(criado, conc));
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("diasAteRespostaDaOng", mediana(resposta));
        r.put("diasAteConclusao", mediana(conclusao));
        r.put("amostraResposta", resposta.size());
        r.put("amostraConclusao", conclusao.size());
        return r;
    }

    // ---------------- helpers ----------------

    private static long[] linha(Map<String, long[]> m, String k) {
        return m.computeIfAbsent(k, x -> new long[6]);
    }

    /** "Limeira - SP" -> "SP"; sem UF reconhecivel -> "Não informado". */
    static String uf(String cidade) {
        if (cidade != null) {
            String t = cidade.trim();
            int i = t.lastIndexOf('-');
            if (i < 0) i = t.lastIndexOf('/');
            if (i >= 0) {
                String s = t.substring(i + 1).trim().toUpperCase(Locale.ROOT);
                if (s.matches("[A-Z]{2}")) return s;
            }
        }
        return "Não informado";
    }

    private static Double pct(long parte, long total) {
        return total == 0 ? null : Math.round(parte * 1000.0 / total) / 10.0;
    }

    private static Double mediana(List<Double> v) {
        if (v.isEmpty()) return null;
        Collections.sort(v);
        int m = v.size() / 2;
        double x = v.size() % 2 == 1 ? v.get(m) : (v.get(m - 1) + v.get(m)) / 2;
        return Math.round(x * 10) / 10.0;
    }

    private static double dias(LocalDateTime a, LocalDateTime b) {
        return Duration.between(a, b).toMinutes() / 1440.0;
    }

    private static String texto(Object o) {
        return o == null ? null : o.toString();
    }

    private static LocalDateTime data(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime l) return l;
        if (o instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (o instanceof LocalDate d) return d.atStartOfDay();
        return null;
    }
}
