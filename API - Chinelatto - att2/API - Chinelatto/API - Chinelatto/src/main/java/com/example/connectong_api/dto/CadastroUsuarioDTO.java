package com.example.connectong_api.dto;

import com.example.connectong_api.validacao.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dados aceitos no cadastro de usuario. Diferente de receber a entidade crua,
 * este DTO NAO expoe "id" nem "ongId" ao cliente, evitando mass assignment
 * (ex.: um doador tentar se cadastrar apontando para o ongId de outra ONG).
 * O "tipo" e limitado a DOADOR/ONG.
 */
public class CadastroUsuarioDTO {

    @NotBlank(message = "O nome é obrigatório")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    @NomeProprio(message = "Nome inválido: use apenas letras (sem números ou símbolos)")
    private String nome;

    @NotBlank(message = "O email é obrigatório")
    @Email(message = "Email inválido")
    private String email;

    @NotBlank(message = "A senha é obrigatória")
    @SenhaForte
    private String senha;

    @NotBlank(message = "O tipo é obrigatório")
    @Pattern(regexp = "DOADOR|ONG", message = "Tipo deve ser DOADOR ou ONG")
    private String tipo;

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    // LGPD (F-04): mesmo aceite obrigatorio do /usuarios/registro.
    @jakarta.validation.constraints.NotNull(message = "É preciso aceitar os Termos de Uso e a Política de Privacidade")
    @jakarta.validation.constraints.AssertTrue(message = "É preciso aceitar os Termos de Uso e a Política de Privacidade")
    private Boolean aceiteTermos;

    public Boolean getAceiteTermos() { return aceiteTermos; }
    public void setAceiteTermos(Boolean aceiteTermos) { this.aceiteTermos = aceiteTermos; }
}
