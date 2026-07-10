package com.example.connectong_api.service;

import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Necessidade;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Aviso periodico para a ONG de doadores esperando ha muito tempo pelo aceite.
 *
 * Regra (pedido do usuario): apos {@code app.espera.dias-inicial} (default 10)
 * dias sem a ONG aceitar/recusar um interesse PENDENTE, notifica a ONG; depois
 * repete a cada {@code app.espera.dias-intervalo} (default 5) dias
 * (10, 15, 20, ...). Assim a ONG nao esquece um doador na fila.
 *
 * Idempotencia SEM coluna nova: o job roda UMA VEZ POR DIA (cron), e cada
 * interesse cruza um dia-limite (10, 15, 20...) em exatamente um dia de
 * calendario — entao cada limiar dispara uma unica notificacao, sem
 * deduplicacao persistida. (A tela do painel ainda mostra "ha N dias esperando"
 * on-read, independente deste job.)
 */
@Component
public class EsperaMatchScheduler {

    @Autowired
    private InteresseRepository interesseRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Value("${app.espera.dias-inicial:10}")
    private int diasInicial;

    @Value("${app.espera.dias-intervalo:5}")
    private int diasIntervalo;

    // Diariamente as 9h (horario do servidor). Configuravel/testavel via cron.
    @Scheduled(cron = "${app.espera.cron:0 0 9 * * *}")
    public void avisarEsperaLonga() {
        LocalDateTime agora = LocalDateTime.now();
        for (Interesse i : interesseRepository.findByStatus("PENDENTE")) {
            try {
                processar(i, agora);
            } catch (Exception ignorado) {
                // best-effort: um interesse com dados inconsistentes nao pode
                // derrubar o aviso dos demais.
            }
        }
    }

    private void processar(Interesse i, LocalDateTime agora) {
        if (i.getDataCriacao() == null) return;
        long dias = ChronoUnit.DAYS.between(i.getDataCriacao(), agora);
        if (!ehDiaDeAvisar(dias)) return;

        Necessidade nec = i.getNecessidade();
        if (nec == null || nec.getOng() == null) return;

        Usuario contaOng = usuarioRepository.findByOngId(nec.getOng().getId()).orElse(null);
        if (contaOng == null || contaOng.getDataExclusao() != null) return;

        String doador = i.getDoador() != null ? i.getDoador().getNome() : "Um doador";
        String titulo = nec.getTitulo() != null ? nec.getTitulo() : "uma necessidade";

        notificacaoService.criar(
                contaOng.getId(),
                "Doador esperando ha " + dias + " dias",
                doador + " está há " + dias + " dias esperando seu aceite em \""
                        + titulo + "\". Que tal responder?",
                "MATCH");
    }

    // Dia-limite: exatamente no dia inicial (10) e depois a cada intervalo (5):
    // 10, 15, 20, 25... Antes do inicial, nao avisa.
    private boolean ehDiaDeAvisar(long dias) {
        if (dias < diasInicial) return false;
        if (diasIntervalo <= 0) return dias == diasInicial;
        return (dias - diasInicial) % diasIntervalo == 0;
    }
}
