package com.example.connectong_api.service;

import com.example.connectong_api.dto.AtividadeResponseDTO;
import com.example.connectong_api.model.Atividade;
import com.example.connectong_api.repository.AtividadeRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Feed global de atividades da plataforma (Timeline).
 *
 * O metodo registrar() segue o mesmo contrato do AuditService: NUNCA propaga
 * excecao. A timeline e um efeito colateral do evento principal (publicar uma
 * necessidade, contribuir numa campanha, etc.) e jamais pode quebra-lo se a
 * gravacao do feed falhar.
 */
@Service
public class AtividadeService {

    private static final int LIMITE_MAX = 100;

    @Autowired
    private AtividadeRepository repository;

    public void registrar(String tipo, String descricao, Long ongId, String ongNome) {
        try {
            Atividade a = new Atividade();
            a.setTipo(tipo);
            a.setDescricao(truncar(descricao, 500));
            a.setOngId(ongId);
            a.setOngNome(truncar(ongNome, 255));
            repository.save(a);
        } catch (Exception ignorado) {
            // feed e best-effort: falha aqui nao afeta a operacao principal
        }
    }

    // Lista as atividades mais recentes (feed global ou filtrado por ONG).
    public List<AtividadeResponseDTO> listar(Long ongId, int limit) {
        int tamanho = limit <= 0 ? 30 : Math.min(limit, LIMITE_MAX);
        PageRequest page = PageRequest.of(0, tamanho);

        List<Atividade> lista = (ongId != null)
                ? repository.findByOngIdOrderByDataCriacaoDesc(ongId, page)
                : repository.findAllByOrderByDataCriacaoDesc(page);

        return lista.stream()
                .map(AtividadeResponseDTO::new)
                .collect(Collectors.toList());
    }

    private String truncar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
