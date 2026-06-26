package com.example.connectong_api.controller;

import com.example.connectong_api.dto.EstatisticasPublicasDTO;
import com.example.connectong_api.repository.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints PUBLICOS (sem login) que alimentam o portal institucional /
 * pagina de transparencia.
 */
@RestController
@RequestMapping("/publico")
@CrossOrigin(origins = "*")
@Tag(name = "Publico", description = "Dados publicos para o portal institucional (transparencia)")
public class PublicoController {

    @Autowired private ONGRepository ongRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private NecessidadeRepository necessidadeRepository;
    @Autowired private InteresseRepository interesseRepository;
    @Autowired private DoacaoFinanceiraRepository doacaoFinanceiraRepository;
    @Autowired private PrestacaoRepository prestacaoRepository;

    @GetMapping("/estatisticas")
    @Operation(summary = "Numeros publicos da plataforma (transparencia / impacto)")
    public EstatisticasPublicasDTO estatisticas() {
        EstatisticasPublicasDTO dto = new EstatisticasPublicasDTO();
        dto.setTotalOngs(ongRepository.count());
        dto.setTotalDoadores(usuarioRepository.countByTipo("DOADOR"));
        dto.setTotalNecessidades(necessidadeRepository.count());
        dto.setTotalMatches(interesseRepository.countByStatus("ACEITO"));
        dto.setTotalDoacoesFinanceiras(doacaoFinanceiraRepository.count());
        dto.setValorTotalDoado(doacaoFinanceiraRepository.somarValores());
        dto.setTotalPrestacoes(prestacaoRepository.count());
        return dto;
    }
}
