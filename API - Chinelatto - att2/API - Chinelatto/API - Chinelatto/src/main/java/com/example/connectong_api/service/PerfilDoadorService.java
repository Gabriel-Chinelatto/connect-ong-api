package com.example.connectong_api.service;

import com.example.connectong_api.dto.PerfilPublicoDoadorDTO;
import com.example.connectong_api.model.Prestacao;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.DoacaoFinanceiraRepository;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.PrestacaoRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Perfil PUBLICO do doador (endpoint sem login, na whitelist do SecurityConfig).
 * PRIVACIDADE: nunca expoe email, telefone nem valores em R$ — as doacoes PIX
 * entram so como CONTAGEM. 404 para conta inexistente, excluida (soft-delete)
 * ou que nao seja DOADOR (perfis de ONG tem o proprio endpoint publico).
 */
@Service
public class PerfilDoadorService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private InteresseRepository interesseRepository;

    @Autowired
    private DoacaoFinanceiraRepository doacaoFinanceiraRepository;

    @Autowired
    private PrestacaoRepository prestacaoRepository;

    @Autowired
    private AvaliacaoDoadorService avaliacaoDoadorService;

    @Transactional(readOnly = true)
    public ResponseEntity<?> perfilPublico(Long id) {
        Usuario u = usuarioRepository.findById(id).orElse(null);
        // 404: nao existe, soft-deleted ou nao e DOADOR.
        if (u == null || u.getDataExclusao() != null
                || !"DOADOR".equals(u.getTipo())) {
            return ResponseEntity.notFound().build();
        }

        PerfilPublicoDoadorDTO.Stats stats = new PerfilPublicoDoadorDTO.Stats(
                interesseRepository.countByDoadorIdAndStatus(id, "CONCLUIDO"),
                doacaoFinanceiraRepository.countByDoadorId(id));

        List<PerfilPublicoDoadorDTO.PrestacaoRecebidaDTO> prestacoesRecebidas =
                prestacaoRepository.findByInteresseDoadorIdOrderByDataCriacaoDesc(id)
                        .stream()
                        .map(this::toPrestacaoRecebida)
                        .collect(Collectors.toList());

        PerfilPublicoDoadorDTO dto = new PerfilPublicoDoadorDTO(
                u.getId(),
                u.getNome(),
                u.getCidade(),
                u.getEstado(),
                u.getFotoBase64(),
                u.getCriadoEm(),
                u.getNotaMediaDoador(),
                u.getTotalAvaliacoesDoador(),
                stats,
                avaliacaoDoadorService.listar(id),
                prestacoesRecebidas);

        return ResponseEntity.ok(dto);
    }

    private PerfilPublicoDoadorDTO.PrestacaoRecebidaDTO toPrestacaoRecebida(Prestacao p) {
        String ongNome = (p.getInteresse() != null
                && p.getInteresse().getNecessidade() != null
                && p.getInteresse().getNecessidade().getOng() != null)
                ? p.getInteresse().getNecessidade().getOng().getNome() : null;
        String necessidadeTitulo = (p.getInteresse() != null
                && p.getInteresse().getNecessidade() != null)
                ? p.getInteresse().getNecessidade().getTitulo() : null;
        return new PerfilPublicoDoadorDTO.PrestacaoRecebidaDTO(
                p.getTitulo(),
                p.getDescricao(),
                ongNome,
                necessidadeTitulo,
                p.getDataCriacao());
    }
}
