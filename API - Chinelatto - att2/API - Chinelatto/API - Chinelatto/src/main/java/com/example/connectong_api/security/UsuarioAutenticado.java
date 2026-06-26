package com.example.connectong_api.security;

/**
 * Identidade do usuario autenticado, extraida do JWT e guardada como "principal"
 * no SecurityContext. Permite checagens de DONO (ownership) sem confiar em ids
 * vindos do corpo/query da requisicao.
 *
 * - id    = id do usuario (subject do token)
 * - tipo  = DOADOR ou ONG
 * - ongId = id da ONG (preenchido so quando tipo = ONG; null para doador)
 */
public class UsuarioAutenticado {

    private final Long id;
    private final String tipo;
    private final Long ongId;

    public UsuarioAutenticado(Long id, String tipo, Long ongId) {
        this.id = id;
        this.tipo = tipo;
        this.ongId = ongId;
    }

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public Long getOngId() { return ongId; }
}
