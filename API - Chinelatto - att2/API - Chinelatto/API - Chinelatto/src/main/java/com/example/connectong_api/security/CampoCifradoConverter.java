package com.example.connectong_api.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Liga a {@link Criptografia} a uma coluna: cifra ao gravar e decifra ao ler,
 * sem que o resto do codigo (services, DTOs, apps) perceba. Uso:
 * {@code @Convert(converter = CampoCifradoConverter.class)}.
 *
 * <p>Se um valor foi adulterado no banco, a leitura NAO derruba a tela inteira
 * (uma lista de conversas com uma mensagem estragada continuaria abrindo):
 * devolve um aviso no lugar do texto e registra o erro no log.
 */
@Converter
public class CampoCifradoConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(CampoCifradoConverter.class);
    public static final String ILEGIVEL = "[conteúdo protegido indisponível]";

    @Override
    public String convertToDatabaseColumn(String valor) {
        return Criptografia.get().cifrar(valor);
    }

    @Override
    public String convertToEntityAttribute(String armazenado) {
        try {
            return Criptografia.get().decifrar(armazenado);
        } catch (Criptografia.DadoAdulteradoException e) {
            log.error("Campo cifrado nao confere (adulterado ou chave trocada)");
            return ILEGIVEL;
        }
    }
}
