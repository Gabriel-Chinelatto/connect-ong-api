package com.example.connectong_api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de unidade das anotacoes de validacao (Bean Validation) nos DTOs.
 * Verifica que dados invalidos sao barrados e validos passam.
 */
class ValidacaoDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void ongRegistroSemCamposObrigatoriosFalha() {
        OngRegistroDTO dto = new OngRegistroDTO(); // tudo nulo
        Set<ConstraintViolation<OngRegistroDTO>> v = validator.validate(dto);
        assertFalse(v.isEmpty(), "DTO vazio deveria ter violacoes");
    }

    @Test
    void ongRegistroValidoPassa() {
        OngRegistroDTO dto = new OngRegistroDTO();
        dto.setNome("Lar Feliz");
        dto.setEmail("contato@larfeliz.org");
        dto.setSenha("senha123"); // senha de ONG agora exige minimo 6 (igual doador)
        Set<ConstraintViolation<OngRegistroDTO>> v = validator.validate(dto);
        assertTrue(v.isEmpty(), "DTO valido nao deveria ter violacoes: " + v);
    }

    @Test
    void doacaoComValorNegativoFalha() {
        DoacaoFinanceiraRequestDTO dto = new DoacaoFinanceiraRequestDTO();
        dto.setOngId(1L);
        dto.setDoadorId(1L);
        dto.setValor(-5.0);
        assertFalse(validator.validate(dto).isEmpty());
    }

    @Test
    void avaliacaoComNotaForaDoIntervaloFalha() {
        AvaliacaoRequestDTO dto = new AvaliacaoRequestDTO();
        dto.setOngId(1L);
        dto.setDoadorId(1L);
        dto.setNota(9); // valido seria 1..5
        assertFalse(validator.validate(dto).isEmpty());
    }
}
