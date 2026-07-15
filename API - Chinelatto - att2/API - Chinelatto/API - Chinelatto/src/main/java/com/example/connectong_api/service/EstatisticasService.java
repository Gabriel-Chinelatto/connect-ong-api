package com.example.connectong_api.service;

import com.example.connectong_api.dto.EstatisticasPublicasDTO;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Numeros publicos da plataforma (transparencia/impacto) em UMA UNICA consulta.
 *
 * MOTIVO (performance): antes o controller fazia 7 chamadas separadas ao
 * repositorio -> 7 idas e voltas ao banco. Com a API hospedada longe do MySQL
 * (backend nos EUA, banco da escola no Brasil), CADA ida custa ~600ms: a tela
 * levava ~4,8s. Agrupando tudo em 1 consulta, sobra 1 ida so (~0,6s).
 *
 * As subconsultas replicam EXATAMENTE as regras dos metodos que substituiram:
 *   - ONGs/doadores: apenas ATIVOS (soft-delete = data_exclusao IS NULL);
 *   - matches: interesses com status 'ACEITO';
 *   - valor total: COALESCE(SUM(valor),0) de TODAS as doacoes financeiras.
 */
@Service
public class EstatisticasService {

    @PersistenceContext
    private EntityManager em;

    // Uma linha, sete colunas. Nativa de proposito: o JPQL nao permite
    // combinar contagens de entidades diferentes numa unica consulta.
    private static final String SQL = """
            SELECT
              (SELECT COUNT(*) FROM ong WHERE data_exclusao IS NULL)                        AS total_ongs,
              (SELECT COUNT(*) FROM usuario WHERE tipo = 'DOADOR' AND data_exclusao IS NULL) AS total_doadores,
              (SELECT COUNT(*) FROM necessidade)                                             AS total_necessidades,
              (SELECT COUNT(*) FROM interesse WHERE status = 'ACEITO')                       AS total_matches,
              (SELECT COUNT(*) FROM doacao_financeira)                                       AS total_doacoes_fin,
              (SELECT COALESCE(SUM(valor), 0) FROM doacao_financeira)                        AS valor_total,
              (SELECT COUNT(*) FROM prestacao)                                               AS total_prestacoes
            """;

    public EstatisticasPublicasDTO publicas() {
        Object[] r = (Object[]) em.createNativeQuery(SQL).getSingleResult();
        EstatisticasPublicasDTO dto = new EstatisticasPublicasDTO();
        dto.setTotalOngs(inteiro(r[0]));
        dto.setTotalDoadores(inteiro(r[1]));
        dto.setTotalNecessidades(inteiro(r[2]));
        dto.setTotalMatches(inteiro(r[3]));
        dto.setTotalDoacoesFinanceiras(inteiro(r[4]));
        dto.setValorTotalDoado(decimal(r[5]));
        dto.setTotalPrestacoes(inteiro(r[6]));
        return dto;
    }

    // O driver pode devolver Long, BigInteger ou BigDecimal conforme a versao
    // do MySQL/Hibernate — normalizamos aqui para nao quebrar.
    private static long inteiro(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static double decimal(Object v) {
        if (v == null) return 0d;
        if (v instanceof BigDecimal bd) return bd.doubleValue();
        return ((Number) v).doubleValue();
    }
}
