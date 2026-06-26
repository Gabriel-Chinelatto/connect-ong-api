package com.example.connectong_api.exception;

/**
 * Lancada quando o usuario esta autenticado, mas tenta acessar/alterar um
 * recurso que NAO e dele (falha de autorizacao/ownership). Mapeada para HTTP 403
 * no GlobalExceptionHandler.
 */
public class AcessoNegadoException extends RuntimeException {
    public AcessoNegadoException(String mensagem) {
        super(mensagem);
    }
}
