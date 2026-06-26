package com.example.connectong_api.service;

import com.example.connectong_api.dto.DenunciaRequestDTO;
import com.example.connectong_api.dto.DenunciaResponseDTO;
import com.example.connectong_api.model.Denuncia;
import com.example.connectong_api.repository.DenunciaRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Registra e gerencia denuncias (reports) feitas pelos usuarios contra alvos da
 * plataforma. O tipoAlvo e normalizado para maiusculas; toda denuncia criada e
 * resolvida e registrada na auditoria. Resolver muda o status para RESOLVIDA.
 */
@Service
public class DenunciaService {

    @Autowired private DenunciaRepository repository;
    @Autowired private AuditService auditService;

    public ResponseEntity<?> criar(DenunciaRequestDTO dto) {
        Denuncia d = new Denuncia();
        d.setDenuncianteId(dto.getDenuncianteId());
        d.setTipoAlvo(dto.getTipoAlvo().trim().toUpperCase());
        d.setAlvoId(dto.getAlvoId());
        d.setMotivo(dto.getMotivo());
        d.setDescricao(dto.getDescricao());

        Denuncia salva = repository.save(d);

        auditService.registrar("DENUNCIA", dto.getDenuncianteId(),
                "Denuncia de " + salva.getTipoAlvo() + " id=" + salva.getAlvoId()
                        + " motivo=" + salva.getMotivo());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DenunciaResponseDTO(salva));
    }

    public List<DenunciaResponseDTO> listar(String status) {
        List<Denuncia> lista = (status != null && !status.isBlank())
                ? repository.findByStatusOrderByDataCriacaoDesc(status.trim().toUpperCase())
                : repository.findAllByOrderByDataCriacaoDesc();
        return lista.stream().map(DenunciaResponseDTO::new).collect(Collectors.toList());
    }

    public ResponseEntity<?> resolver(Long id) {
        Denuncia d = repository.findById(id).orElse(null);
        if (d == null) return ResponseEntity.notFound().build();
        d.setStatus("RESOLVIDA");
        return ResponseEntity.ok(new DenunciaResponseDTO(repository.save(d)));
    }
}
