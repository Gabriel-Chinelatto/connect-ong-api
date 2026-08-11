package com.example.connectong_api.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * Tratamento central de erros da API.
 * Garante que toda falha retorne um JSON limpo e consistente,
 * em vez de um stacktrace ou mensagem generica do servidor.
 *
 * Hardening: erros do cliente (400) sao explicados; erros internos (500)
 * retornam mensagem generica e NUNCA expoem detalhes internos (SQL,
 * stacktrace, caminhos), evitando vazamento de informacao.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Erros de validacao de corpo (@Valid) -> 400 com a lista de campos invalidos
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> tratarValidacao(
            MethodArgumentNotValidException ex) {

        Map<String, String> camposInvalidos = new HashMap<>();
        String primeiraMensagem = null;
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            camposInvalidos.put(erro.getField(), erro.getDefaultMessage());
            if (primeiraMensagem == null) {
                primeiraMensagem = erro.getDefaultMessage();
            }
        }

        Map<String, Object> corpo = new HashMap<>();
        // "erro" traz uma mensagem legivel (a do primeiro campo invalido), pois os
        // apps exibem body['erro'] direto ao usuario; "campos" detalha o restante.
        corpo.put("erro", primeiraMensagem != null ? primeiraMensagem : "Dados invalidos");
        corpo.put("campos", camposInvalidos);

        return ResponseEntity.badRequest().body(corpo);
    }

    // Violacoes de constraint (parametros @Validated OU validacao de entidade em
    // tempo de persist) -> 400. Extraimos SO as mensagens legiveis das violacoes;
    // nunca devolvemos ex.getMessage() cru, que vazaria nome de classe, propertyPath
    // e detalhes internos (information disclosure).
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> tratarConstraint(
            ConstraintViolationException ex) {
        String mensagem = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("Dados invalidos.");
        return badRequest(mensagem);
    }

    // JSON malformado ou tipo incompativel no corpo -> 400 (e nao 500)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> tratarJsonInvalido(
            HttpMessageNotReadableException ex) {
        return badRequest("Corpo da requisicao invalido ou mal formatado.");
    }

    // Tipo errado em parametro de rota/query (ex.: id=abc) -> 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> tratarTipoInvalido(
            MethodArgumentTypeMismatchException ex) {
        return badRequest("Parametro '" + ex.getName() + "' com valor invalido.");
    }

    // Violacao de integridade no banco (ex.: texto maior que a coluna, valor
    // duplicado numa coluna unica) -> 400 com mensagem generica. Evita que esses
    // casos virem 500 e NUNCA expoe o SQL/constraint interno ao cliente.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> tratarIntegridade(
            DataIntegrityViolationException ex) {
        return badRequest("Nao foi possivel salvar: verifique os dados enviados "
                + "(algum campo pode estar longo demais ou duplicado).");
    }

    // Falha de autorizacao/ownership (usuario mexendo em dado de outro) -> 403
    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<Map<String, String>> tratarAcessoNegado(
            AcessoNegadoException ex) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(corpo);
    }

    // Parametro de query OBRIGATORIO ausente (ex.: /favoritos/ids sem
    // usuarioId) -> 400. Sem este handler a requisicao caia no generico e a API
    // respondia 500 "erro inesperado no servidor" para um erro do CLIENTE:
    // confunde quem consome a API e polui o monitoramento com falso alarme de
    // servidor quebrado (achado na varredura de 10/08/2026).
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> tratarParametroAusente(
            MissingServletRequestParameterException ex) {
        return badRequest("Parametro obrigatorio '" + ex.getParameterName() + "' nao foi informado.");
    }

    // Rota inexistente -> 404 (e nao 500). Ex.: chamar /usuarios/me quando o
    // correto e /auth/me.
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, String>> tratarRotaInexistente(Exception ex) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", "Endereco nao encontrado nesta API.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(corpo);
    }

    // Validacao da ENTIDADE que estoura no commit da transacao. O Spring
    // embrulha a ConstraintViolationException numa TransactionSystemException,
    // que escapava do handler acima e virava 500 — foi assim que "salvar o
    // perfil da ONG" respondia 500 ao enviar e-mail vazio.
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<Map<String, String>> tratarErroDeTransacao(
            TransactionSystemException ex) {
        if (ex.getMostSpecificCause() instanceof ConstraintViolationException cve) {
            String detalhe = cve.getConstraintViolations().stream()
                    .findFirst()
                    .map(v -> "Campo invalido: " + v.getPropertyPath() + " " + v.getMessage())
                    .orElse("Dados invalidos.");
            return badRequest(detalhe);
        }
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", "Ocorreu um erro inesperado no servidor.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(corpo);
    }

    // Qualquer outra excecao nao prevista -> 500 com mensagem generica (sem vazar detalhes)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> tratarGenerico(Exception ex) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", "Ocorreu um erro inesperado no servidor.");

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(corpo);
    }

    private ResponseEntity<Map<String, String>> badRequest(String mensagem) {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("erro", mensagem);
        return ResponseEntity.badRequest().body(corpo);
    }
}
