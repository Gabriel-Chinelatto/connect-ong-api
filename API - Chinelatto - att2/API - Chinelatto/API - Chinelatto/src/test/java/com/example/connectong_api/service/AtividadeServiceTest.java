package com.example.connectong_api.service;

import com.example.connectong_api.dto.AtividadeResponseDTO;
import com.example.connectong_api.model.Atividade;
import com.example.connectong_api.repository.AtividadeRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AtividadeServiceTest {

    @Mock
    private AtividadeRepository repository;

    @InjectMocks
    private AtividadeService service;

    @Test
    void registrarNuncaLancaMesmoComFalhaNoRepositorio() {
        // O feed e best-effort: uma falha ao gravar nao pode quebrar o fluxo.
        when(repository.save(any())).thenThrow(new RuntimeException("db down"));
        assertDoesNotThrow(() ->
                service.registrar("CAMPANHA", "A campanha atingiu a meta!", 1L, "Lar Viva"));
    }

    @Test
    void listarMapeiaAtividadesParaDto() {
        Atividade a = new Atividade();
        a.setTipo("DOACAO");
        a.setDescricao("Lar Viva recebeu uma nova doacao via PIX");
        a.setOngId(7L);
        a.setOngNome("Lar Viva");
        when(repository.findAllByOrderByDataCriacaoDesc(any())).thenReturn(List.of(a));

        List<AtividadeResponseDTO> dtos = service.listar(null, 30);

        assertEquals(1, dtos.size());
        assertEquals("DOACAO", dtos.get(0).getTipo());
        assertEquals(7L, dtos.get(0).getOngId());
        assertEquals("Lar Viva", dtos.get(0).getOngNome());
    }
}
