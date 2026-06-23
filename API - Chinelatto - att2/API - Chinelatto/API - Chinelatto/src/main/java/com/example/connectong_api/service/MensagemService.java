package com.example.connectong_api.service;

import com.example.connectong_api.dto.MensagemRequestDTO;
import com.example.connectong_api.dto.MensagemResponseDTO;
import com.example.connectong_api.model.Interesse;
import com.example.connectong_api.model.Mensagem;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.MensagemRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MensagemService {

    @Autowired
    private MensagemRepository repository;

    @Autowired
    private InteresseRepository interesseRepository;

    // Lista as mensagens de um match, em ordem cronologica.
    public List<MensagemResponseDTO> listar(Long interesseId) {
        return repository.findByInteresseIdOrderByDataEnvioAsc(interesseId)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // Envia uma mensagem (so apos o match ser aceito).
    public ResponseEntity<?> enviar(MensagemRequestDTO dto) {

        if (dto.getInteresseId() == null) {
            return erro("É obrigatório informar o interesseId (o match)");
        }

        if (dto.getConteudo() == null || dto.getConteudo().isBlank()) {
            return erro("A mensagem não pode ser vazia");
        }

        final String remetente = dto.getRemetente();
        if (remetente == null
                || (!remetente.equals("DOADOR") && !remetente.equals("ONG"))) {
            return erro("Remetente inválido (use DOADOR ou ONG)");
        }

        Interesse interesse =
                interesseRepository.findById(dto.getInteresseId()).orElse(null);
        if (interesse == null) {
            return erro("Match não encontrado");
        }

        if (!"ACEITO".equals(interesse.getStatus())) {
            return erro("O chat só fica disponível após o match ser aceito");
        }

        Mensagem mensagem = new Mensagem();
        mensagem.setInteresse(interesse);
        mensagem.setRemetente(remetente);
        mensagem.setConteudo(dto.getConteudo());

        Mensagem salva = repository.save(mensagem);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(toDTO(salva));
    }

    private MensagemResponseDTO toDTO(Mensagem m) {
        return new MensagemResponseDTO(
                m.getId(),
                m.getInteresse() != null ? m.getInteresse().getId() : null,
                m.getRemetente(),
                m.getConteudo(),
                m.getDataEnvio()
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
