package com.example.connectong_api.security;

import com.example.connectong_api.exception.AcessoNegadoException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Helpers de AUTORIZACAO (ownership). Enquanto o Spring Security garante que a
 * requisicao tem um token valido (autenticacao), estes metodos garantem que o
 * usuario so mexe nos PROPRIOS dados (autorizacao) — comparando o id/ongId do
 * recurso com a identidade do token, nunca com ids vindos do cliente.
 *
 * Lancam AcessoNegadoException (HTTP 403) quando a checagem falha.
 */
@Component
public class SecurityUtils {

    /** Identidade do token, ou null se nao autenticado. */
    public UsuarioAutenticado atual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UsuarioAutenticado u) {
            return u;
        }
        return null;
    }

    public Long usuarioId() {
        UsuarioAutenticado u = atual();
        return u != null ? u.getId() : null;
    }

    public Long ongIdAtual() {
        UsuarioAutenticado u = atual();
        return u != null ? u.getOngId() : null;
    }

    /** Exige que o id informado seja o do proprio usuario autenticado. */
    public void exigirUsuario(Long idAlvo) {
        Long meu = usuarioId();
        if (meu == null || idAlvo == null || !meu.equals(idAlvo)) {
            throw new AcessoNegadoException(
                    "Voce nao tem permissao para acessar dados de outro usuario.");
        }
    }

    /** Exige que o ongId informado seja o da ONG do proprio usuario autenticado. */
    public void exigirOng(Long ongIdAlvo) {
        Long minhaOng = ongIdAtual();
        if (minhaOng == null || ongIdAlvo == null || !minhaOng.equals(ongIdAlvo)) {
            throw new AcessoNegadoException(
                    "Voce nao tem permissao para acessar dados de outra ONG.");
        }
    }

    /**
     * Exige que o usuario autenticado seja DONO do recurso por um dos dois lados:
     * ser o usuario (doador) dono, OU ser a ONG dona. Usado no chat (o doador do
     * match ou a ONG do match podem ler/escrever).
     */
    public void exigirUsuarioOuOng(Long usuarioIdAlvo, Long ongIdAlvo) {
        Long meu = usuarioId();
        Long minhaOng = ongIdAtual();
        boolean ehDono =
                (usuarioIdAlvo != null && usuarioIdAlvo.equals(meu))
                        || (ongIdAlvo != null && ongIdAlvo.equals(minhaOng));
        if (!ehDono) {
            throw new AcessoNegadoException(
                    "Voce nao participa desta conversa.");
        }
    }
}
