package com.example.connectong_api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Uma mensagem de chat trocada dentro de um match (Interesse ACEITO).
 * remetente = "DOADOR" ou "ONG" (quem enviou).
 */
@Entity
@Table(name = "mensagem")
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY: o chat lista muitas mensagens; carregar o Interesse (e toda a
    // cadeia Necessidade->Ong e Doador->Usuario) em cada uma seria desnecessario
    // e ainda arrastaria o hash de senha do doador. O toDTO usa so o id.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interesse_id")
    private Interesse interesse;

    private String remetente;

    @Column(length = 1000)
    private String conteudo;

    private LocalDateTime dataEnvio;

    // Recibo de leitura ("visto"). null = ainda nao lida; preenchida com o momento
    // em que o outro participante abriu o chat. Usamos datetime (e nao um boolean)
    // para evitar o gotcha BIT/TINYINT com ddl-auto=validate no MySQL 5.6 e ja
    // guardar QUANDO foi lida. O cliente mostra 1 check (enviada) ou 2 (lida).
    private LocalDateTime dataLeitura;

    public Mensagem() {}

    @PrePersist
    public void aoCriar() {
        this.dataEnvio = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public Interesse getInteresse() { return interesse; }
    public void setInteresse(Interesse interesse) { this.interesse = interesse; }

    public String getRemetente() { return remetente; }
    public void setRemetente(String remetente) { this.remetente = remetente; }

    public String getConteudo() { return conteudo; }
    public void setConteudo(String conteudo) { this.conteudo = conteudo; }

    public LocalDateTime getDataEnvio() { return dataEnvio; }

    public LocalDateTime getDataLeitura() { return dataLeitura; }
    public void setDataLeitura(LocalDateTime dataLeitura) { this.dataLeitura = dataLeitura; }
}
