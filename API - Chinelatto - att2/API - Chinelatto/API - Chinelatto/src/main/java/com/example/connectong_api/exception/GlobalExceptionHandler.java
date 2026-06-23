package com.example.connectong_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * Tratamento central de erros da API.
 * Garante que toda falha retorne um JSON limpo e consistente,
 * em vez de um stacktrace ou mensagem generica do servidor.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Erros de validacao (@Valid) -> 400 com a lista de campos invalidos
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacao(
            MethodArgumentNotValidException ex) {

        Map<String, String> camposInvalidos = new HashMap<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            camposInvalidos.put(erro.getField(), erro.getDefaultMessage());
        }

        Map<String, Object> corpo = new HashMap<>();
        corpo.put("erro", "Dados invalidos");
        corpo.put("campos", camposInvalidos);

        return ResponseEntity.badRequest().body(corpo);
    }

    // Qualquer outra excecao nao prevista -> 500 com mensagem amigavel
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> tratarGenerico(Exception ex) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", "Ocorreu um erro inesperado no servidor.");
        corpo.put("detalhe", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(corpo);
    }
}
