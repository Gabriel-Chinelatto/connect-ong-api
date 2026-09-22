package com.example.connectong_api.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Criptografia de dados sensiveis EM REPOUSO (item F-03 do Plano de Acao,
 * apontado pelo avaliador da FECITEC: "nao tem criptografia em dados sensiveis").
 *
 * <p><b>Cifra:</b> AES-256-GCM (confidencialidade + integridade: um byte
 * adulterado no banco faz a leitura FALHAR em vez de devolver lixo). Cada valor
 * ganha um IV aleatorio de 12 bytes, entao o mesmo telefone gravado duas vezes
 * gera dois textos cifrados diferentes (nao da para descobrir quem tem o mesmo
 * numero comparando colunas).
 *
 * <p><b>Formato gravado:</b> {@code enc:v1:<base64(iv || cifrado || tag)>}. O
 * prefixo marca o que ja esta cifrado; valor SEM o prefixo e dado legado em
 * texto puro (anterior a esta mudanca) e e devolvido como esta, ate a
 * {@code CriptografiaMigracao} cifra-lo no proximo startup.
 *
 * <p><b>Chave:</b> {@code app.crypto.key} (base64 de 32 bytes, variavel de
 * ambiente {@code APP_CRYPTO_KEY}), fora do codigo e do repositorio. Precisa
 * ser a MESMA em todo ambiente que usa o mesmo banco (Render, IntelliJ de
 * quem desenvolve, notebook da feira): por isso ela NAO e derivada do segredo
 * do JWT, que e diferente em cada ambiente. Sem a chave, a criptografia fica
 * DESLIGADA (grava texto puro, avisa no log) em vez de inventar uma chave
 * propria — e a {@code CriptografiaMigracao} confere a "impressao digital" da
 * chave guardada no banco: ambiente com a chave errada nao sobe.
 *
 * <p><b>HMAC:</b> {@link #hmac(String)} e usado para os codigos de 6 digitos
 * (recuperacao de senha e 2FA). Um hash comum de 6 digitos se quebra por forca
 * bruta em segundos (so 1 milhao de possibilidades); com HMAC e preciso ter a
 * chave do servidor. Esses codigos vivem 15 minutos e nunca mudam de ambiente,
 * entao a chave do HMAC pode vir do segredo do JWT, e funciona mesmo sem
 * APP_CRYPTO_KEY.
 */
@Component
public class Criptografia {

    private static final Logger log = LoggerFactory.getLogger(Criptografia.class);

    public static final String PREFIXO = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    // Acesso estatico para o conversor JPA (o Hibernate instancia conversores
    // fora do alcance garantido da injecao do Spring).
    private static volatile Criptografia instancia;

    private final SecureRandom random = new SecureRandom();

    @Value("${app.crypto.key:}")
    private String chaveBase64;

    @Value("${app.jwt.secret}")
    private String segredoJwt;

    private SecretKeySpec chaveAes;   // null = criptografia em repouso desligada
    private SecretKeySpec chaveHmac;
    private String impressaoDigital;  // identifica a chave sem revela-la

    @PostConstruct
    void iniciar() throws GeneralSecurityException {
        if (chaveBase64 != null && !chaveBase64.isBlank()) {
            byte[] material = Base64.getDecoder().decode(chaveBase64.trim());
            if (material.length != 32) {
                throw new IllegalStateException(
                        "app.crypto.key precisa ter 32 bytes em base64 (AES-256); tem " + material.length);
            }
            chaveAes = new SecretKeySpec(material, "AES");
            impressaoDigital = HexFormat.of().formatHex(derivar(material, "connect-ong:impressao-digital:v1"));
            log.info("Criptografia em repouso LIGADA (AES-256-GCM, chave APP_CRYPTO_KEY)");
        } else {
            log.warn("Criptografia em repouso DESLIGADA: defina APP_CRYPTO_KEY (32 bytes em base64). "
                    + "Telefones, mensagens e denuncias serao gravados em texto puro.");
        }
        chaveHmac = new SecretKeySpec(
                derivar(segredoJwt.getBytes(StandardCharsets.UTF_8), "connect-ong:hmac-codigos:v1"), "HmacSHA256");
        instancia = this;
    }

    /** true quando APP_CRYPTO_KEY esta configurada. */
    public boolean ligada() {
        return chaveAes != null;
    }

    /** HMAC da chave (64 hex): permite conferir se dois ambientes usam a mesma chave sem expo-la. */
    public String impressaoDigital() {
        return impressaoDigital;
    }

    public static Criptografia get() {
        Criptografia c = instancia;
        if (c == null) {
            throw new IllegalStateException("Criptografia ainda nao inicializada");
        }
        return c;
    }

    public static boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIXO);
    }

    /** Cifra o texto. null continua null; valor ja cifrado nao e cifrado de novo; sem chave, volta como esta. */
    public String cifrar(String texto) {
        if (texto == null || estaCifrado(texto) || chaveAes == null) return texto;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, chaveAes, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] tudo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIXO + Base64.getEncoder().encodeToString(tudo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    /**
     * Decifra. Texto sem o prefixo e legado em texto puro e volta como esta.
     *
     * @throws DadoAdulteradoException se o conteudo cifrado foi alterado ou a
     *         chave e outra (a tag do GCM nao confere).
     */
    public String decifrar(String armazenado) {
        if (armazenado == null || !estaCifrado(armazenado)) return armazenado;
        if (chaveAes == null) throw new DadoAdulteradoException();
        try {
            byte[] tudo = Base64.getDecoder().decode(armazenado.substring(PREFIXO.length()));
            if (tudo.length <= IV_BYTES) throw new DadoAdulteradoException();
            byte[] iv = Arrays.copyOfRange(tudo, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, chaveAes, new GCMParameterSpec(TAG_BITS, iv));
            byte[] claro = cipher.doFinal(tudo, IV_BYTES, tudo.length - IV_BYTES);
            return new String(claro, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new DadoAdulteradoException();
        }
    }

    /** HMAC-SHA256 em hexadecimal (64 caracteres). */
    public String hmac(String valor) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(chaveHmac);
            return HexFormat.of().formatHex(mac.doFinal(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha no HMAC", e);
        }
    }

    private static byte[] derivar(byte[] segredo, String rotulo) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo, "HmacSHA256"));
        return mac.doFinal(rotulo.getBytes(StandardCharsets.UTF_8));
    }

    /** O conteudo cifrado nao confere (foi alterado no banco ou a chave mudou). */
    public static class DadoAdulteradoException extends RuntimeException {
        public DadoAdulteradoException() {
            super("Dado cifrado invalido: adulterado ou cifrado com outra chave");
        }
    }
}
