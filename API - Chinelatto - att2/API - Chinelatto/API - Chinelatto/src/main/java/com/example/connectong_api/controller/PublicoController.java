package com.example.connectong_api.controller;

import com.example.connectong_api.dto.EstatisticasPublicasDTO;
import com.example.connectong_api.service.EstatisticasService;
import com.example.connectong_api.service.TransparenciaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints PUBLICOS (sem login) que alimentam o portal institucional /
 * pagina de transparencia.
 */
@RestController
@RequestMapping("/publico")
@Tag(name = "Publico", description = "Dados publicos para o portal institucional (transparencia)")
public class PublicoController {

    @Autowired private EstatisticasService estatisticasService;
    @Autowired private TransparenciaService transparenciaService;

    @GetMapping("/estatisticas")
    @Operation(summary = "Numeros publicos da plataforma (transparencia / impacto)")
    public EstatisticasPublicasDTO estatisticas() {
        // Delegado ao service, que traz os 7 numeros em UMA consulta so.
        // (Antes eram 7 chamadas separadas = 7 idas ao banco; com o banco longe
        // do servidor isso custava ~4,8s. Ver EstatisticasService.)
        return estatisticasService.publicas();
    }

    @GetMapping("/ranking")
    @Operation(summary = "Ranking de transparencia das ONGs (score + nivel bronze/prata/ouro)")
    public ResponseEntity<?> ranking(@RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok(transparenciaService.ranking(limite));
    }
}
