package com.example.connectong_api.dto;

import java.time.LocalDateTime;

/**
 * Uma pendencia de prestacao de contas: match CONCLUIDO que ainda nao recebeu
 * nenhuma prestacao. O prazo e de 10 dias a partir da conclusao; estourou =>
 * "definitivo" (a pendencia derruba o score de transparencia da ONG em 5
 * pontos — ver TransparenciaService). Tudo calculado on-read, sem cron.
 */
public class PendenciaPrestacaoDTO {

    private Long interesseId;
    private String necessidadeTitulo;
    private String doadorNome;
    private LocalDateTime dataConclusao;
    private int diasRestantes;   // 0 quando o prazo estourou
    private boolean definitivo;  // true quando o prazo estourou

    public PendenciaPrestacaoDTO(Long interesseId, String necessidadeTitulo,
                                 String doadorNome, LocalDateTime dataConclusao,
                                 int diasRestantes, boolean definitivo) {
        this.interesseId = interesseId;
        this.necessidadeTitulo = necessidadeTitulo;
        this.doadorNome = doadorNome;
        this.dataConclusao = dataConclusao;
        this.diasRestantes = diasRestantes;
        this.definitivo = definitivo;
    }

    public Long getInteresseId() { return interesseId; }
    public String getNecessidadeTitulo() { return necessidadeTitulo; }
    public String getDoadorNome() { return doadorNome; }
    public LocalDateTime getDataConclusao() { return dataConclusao; }
    public int getDiasRestantes() { return diasRestantes; }
    public boolean isDefinitivo() { return definitivo; }
}
