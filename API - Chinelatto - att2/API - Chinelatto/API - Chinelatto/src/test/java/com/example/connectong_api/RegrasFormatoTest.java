package com.example.connectong_api;

import com.example.connectong_api.security.RedatorDadosPessoais;
import com.example.connectong_api.validacao.Regras;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F-01 (validacao de formato) e F-10 (redator de dados pessoais), sem Spring.
 * Cada caso invalido aqui e algo que a API ACEITAVA antes da FECITEC.
 */
class RegrasFormatoTest {

    @ParameterizedTest
    @ValueSource(strings = {"(19) 99876-5432", "19998765432", "+55 19 99876-5432", "(19) 3451-2000", "11 2345-6789"})
    void telefonesValidos(String t) { assertTrue(Regras.telefone(t), t); }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "123", "(19) 8888-7777", "(00) 99876-5432", "(19) 89876-5432",
            "(19) 99999-9999", "(19) 9876-54321x", "999999999999999"})
    void telefonesInvalidos(String t) { assertFalse(Regras.telefone(t), t); }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-81", "11222333000181", "12.ABC.345/01DE-35"})
    void cnpjsValidos(String c) { assertTrue(Regras.cnpj(c), c); }

    @ParameterizedTest
    @ValueSource(strings = {"11.222.333/0001-82", "00.000.000/0000-00", "1234", "abcdefghijklmn", "11.222.333/0001-8A"})
    void cnpjsInvalidos(String c) { assertFalse(Regras.cnpj(c), c); }

    @Test
    void textoLegivel() {
        assertTrue(Regras.textoLegivel("Cestas básicas para 30 famílias"));
        assertFalse(Regras.textoLegivel("!!!!"));
        assertFalse(Regras.textoLegivel("12345"));
        assertFalse(Regras.textoLegivel("<script>alert(1)</script>"));
        assertTrue(Regras.textoLegivel(null), "vazio e papel do @NotBlank");
    }

    @Test
    void nomeProprio() {
        assertTrue(Regras.nomeProprio("João D'Ávila-Souza"));
        assertTrue(Regras.nomeProprio("Limeira - SP"));
        assertFalse(Regras.nomeProprio("Jo4o"));
        assertFalse(Regras.nomeProprio("@@@"));
    }

    @Test
    void uf() {
        assertTrue(Regras.uf("sp"));
        assertTrue(Regras.uf("São Paulo"));
        assertTrue(Regras.uf("mato  grosso do sul"));
        assertFalse(Regras.uf("XX"));
        assertFalse(Regras.uf("Narnia"));
    }

    @Test
    void senhaForte() {
        assertTrue(Regras.senhaForte("doacao2026"));
        assertFalse(Regras.senhaForte("123456"), "curta e so numeros");
        assertFalse(Regras.senhaForte("abcdefgh"), "sem numero");
        assertFalse(Regras.senhaForte("12345678"), "sem letra");
        assertFalse(Regras.senhaForte("Senha123"), "senha comum");
    }

    @Test
    void redatorTiraContatosEDocumentos() {
        String r = RedatorDadosPessoais.redigir(
                "Sou a Ana, ana.silva@gmail.com, zap (19) 99876-5432, fixo 19 3451-2000, "
                        + "CPF 123.456.789-09, CNPJ 11.222.333/0001-81. Quero doar 20 cestas em 2026.");
        assertFalse(r.contains("ana.silva"), r);
        assertFalse(r.contains("99876"), r);
        assertFalse(r.contains("3451"), r);
        assertFalse(r.contains("123.456"), r);
        assertFalse(r.contains("11.222"), r);
        assertTrue(r.contains("Quero doar 20 cestas em 2026"), "o resto do texto continua: " + r);
        assertTrue(r.contains("[e-mail]") && r.contains("[telefone]") && r.contains("[CPF]") && r.contains("[CNPJ]"), r);
    }
}
