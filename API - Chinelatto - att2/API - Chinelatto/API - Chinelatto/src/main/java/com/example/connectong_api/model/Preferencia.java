package com.example.connectong_api.model;

import jakarta.persistence.*;

/**
 * Preferencias/configuracoes de um usuario (uma por usuario).
 * Persistidas no banco e carregadas apos o login.
 */
@Entity
@Table(name = "preferencia")
public class Preferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long usuarioId;

    // ----- Aparencia -----
    private String tema;          // CLARO, ESCURO, AUTOMATICO
    private String tamanhoFonte;  // PEQUENA, MEDIA, GRANDE
    private Boolean altoContraste;
    private Boolean fonteDislexia;
    private Boolean navegacaoSimplificada;

    // ----- Notificacoes -----
    private Boolean notifMensagens;
    private Boolean notifMatch;
    private Boolean notifCampanhas;
    private Boolean notifNecessidades;
    private Boolean notifNoticias;

    // ----- Privacidade -----
    private Boolean mostrarTelefone;
    private Boolean mostrarEmail;
    private Boolean perfilPublico;
    private Boolean receberContatos;
    private Boolean receberSugestoes;

    // ----- Seguranca -----
    // Verificacao em duas etapas (2FA). Int 0/1 (NAO boolean): segue o padrao do
    // projeto no MySQL 5.6 (a coluna nasce int via Liquibase, sem o gotcha
    // BIT/TINYINT do ddl-auto=validate). null (contas antigas, sem a coluna
    // preenchida) e 0 significam DESLIGADO; so 1 exige o codigo no login.
    @Column(name = "dois_fatores")
    private Integer doisFatores;

    public Preferencia() {}

    /** Valores padrao para um usuario novo. */
    public static Preferencia padrao(Long usuarioId) {
        Preferencia p = new Preferencia();
        p.usuarioId = usuarioId;
        p.tema = "AUTOMATICO";
        p.tamanhoFonte = "MEDIA";
        p.altoContraste = false;
        p.fonteDislexia = false;
        p.navegacaoSimplificada = false;
        p.notifMensagens = true;
        p.notifMatch = true;
        p.notifCampanhas = true;
        p.notifNecessidades = true;
        p.notifNoticias = true;
        p.mostrarTelefone = true;
        p.mostrarEmail = false;
        p.perfilPublico = true;
        p.receberContatos = true;
        p.receberSugestoes = true;
        p.doisFatores = 0; // 2FA desligado por padrao (logins existentes nao mudam)
        return p;
    }

    public Long getId() { return id; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }

    public String getTema() { return tema; }
    public void setTema(String tema) { this.tema = tema; }

    public String getTamanhoFonte() { return tamanhoFonte; }
    public void setTamanhoFonte(String tamanhoFonte) { this.tamanhoFonte = tamanhoFonte; }

    public Boolean getAltoContraste() { return altoContraste; }
    public void setAltoContraste(Boolean altoContraste) { this.altoContraste = altoContraste; }

    public Boolean getFonteDislexia() { return fonteDislexia; }
    public void setFonteDislexia(Boolean fonteDislexia) { this.fonteDislexia = fonteDislexia; }

    public Boolean getNavegacaoSimplificada() { return navegacaoSimplificada; }
    public void setNavegacaoSimplificada(Boolean v) { this.navegacaoSimplificada = v; }

    public Boolean getNotifMensagens() { return notifMensagens; }
    public void setNotifMensagens(Boolean v) { this.notifMensagens = v; }

    public Boolean getNotifMatch() { return notifMatch; }
    public void setNotifMatch(Boolean v) { this.notifMatch = v; }

    public Boolean getNotifCampanhas() { return notifCampanhas; }
    public void setNotifCampanhas(Boolean v) { this.notifCampanhas = v; }

    public Boolean getNotifNecessidades() { return notifNecessidades; }
    public void setNotifNecessidades(Boolean v) { this.notifNecessidades = v; }

    public Boolean getNotifNoticias() { return notifNoticias; }
    public void setNotifNoticias(Boolean v) { this.notifNoticias = v; }

    public Boolean getMostrarTelefone() { return mostrarTelefone; }
    public void setMostrarTelefone(Boolean v) { this.mostrarTelefone = v; }

    public Boolean getMostrarEmail() { return mostrarEmail; }
    public void setMostrarEmail(Boolean v) { this.mostrarEmail = v; }

    public Boolean getPerfilPublico() { return perfilPublico; }
    public void setPerfilPublico(Boolean v) { this.perfilPublico = v; }

    public Boolean getReceberContatos() { return receberContatos; }
    public void setReceberContatos(Boolean v) { this.receberContatos = v; }

    public Boolean getReceberSugestoes() { return receberSugestoes; }
    public void setReceberSugestoes(Boolean v) { this.receberSugestoes = v; }

    public Integer getDoisFatores() { return doisFatores; }
    public void setDoisFatores(Integer doisFatores) { this.doisFatores = doisFatores; }
}
