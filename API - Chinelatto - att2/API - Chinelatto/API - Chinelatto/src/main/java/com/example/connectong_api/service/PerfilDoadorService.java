package com.example.connectong_api.service;

import com.example.connectong_api.dto.PerfilPublicoDoadorDTO;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.model.Prestacao;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.DoacaoFinanceiraRepository;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.PreferenciaRepository;
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
 * PRIVACIDADE: nunca expoe valores em R$ (doacoes PIX entram so como CONTAGEM).
 * email/telefone so aparecem quando o doador ligou os toggles mostrarEmail/
 * mostrarTelefone (mesma regra do perfil publico da ONG); do contrario, null.
 * 404 para conta inexistente, excluida (soft-delete) ou que nao seja DOADOR
 * (perfis de ONG tem o proprio endpoint publico).
 */
@Service
public class PerfilDoadorService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PreferenciaRepository preferenciaRepository;

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

        // Privacidade real: email/telefone so entram no payload quando o proprio
        // doador ligou o toggle correspondente. Sem preferencia salva => ocultos
        // (o campo vai null e o DTO omite via @JsonInclude(NON_NULL)).
        Preferencia pref = preferenciaRepository.findByUsuarioId(id).orElse(null);
        String emailPublico = (pref != null && Boolean.TRUE.equals(pref.getMostrarEmail()))
                ? u.getEmail() : null;
        String telefonePublico = (pref != null && Boolean.TRUE.equals(pref.getMostrarTelefone()))
                ? u.getTelefone() : null;

        PerfilPublicoDoadorDTO dto = new PerfilPublicoDoadorDTO(
                u.getId(),
                u.getNome(),
                u.getCidade(),
                u.getEstado(),
                u.getFotoBase64(),
                emailPublico,
                telefonePublico,
                u.getCriadoEm(),
                u.getNotaMediaDoador(),
                u.getTotalAvaliacoesDoador(),
                stats,
                avaliacaoDoadorService.listar(id),
                prestacoesRecebidas);

        return ResponseEntity.ok(dto);
    }

    private PerfilPublicoDoadorDTO.PrestacaoRecebidaDTO toPrestacaoRecebida(Prestacao p) {
        boolean temOng = p.getInteresse() != null
                && p.getInteresse().getNecessidade() != null
                && p.getInteresse().getNecessidade().getOng() != null;
        Long ongId = temOng
                ? p.getInteresse().getNecessidade().getOng().getId() : null;
        String ongNome = temOng
                ? p.getInteresse().getNecessidade().getOng().getNome() : null;
        String necessidadeTitulo = (p.getInteresse() != null
                && p.getInteresse().getNecessidade() != null)
                ? p.getInteresse().getNecessidade().getTitulo() : null;
        return new PerfilPublicoDoadorDTO.PrestacaoRecebidaDTO(
                p.getTitulo(),
                p.getDescricao(),
                ongId,
                ongNome,
                necessidadeTitulo,
                p.getDataCriacao());
    }
}
