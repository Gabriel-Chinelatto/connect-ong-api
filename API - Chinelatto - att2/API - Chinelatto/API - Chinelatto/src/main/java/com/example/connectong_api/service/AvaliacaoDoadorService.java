package com.example.connectong_api.service;

import com.example.connectong_api.dto.AvaliacaoDoadorRequestDTO;
import com.example.connectong_api.dto.AvaliacaoDoadorResponseDTO;
import com.example.connectong_api.exception.AcessoNegadoException;
import com.example.connectong_api.model.AvaliacaoDoador;
import com.example.connectong_api.model.AvaliacaoDoadorFoto;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.AvaliacaoDoadorFotoRepository;
import com.example.connectong_api.repository.AvaliacaoDoadorRepository;
import com.example.connectong_api.repository.InteresseRepository;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.UsuarioRepository;
import com.example.connectong_api.security.SecurityUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Avaliacao de DOADOR: a ONG da uma nota (1-5) + comentario ao doador — o
 * espelho da avaliacao de ONG. Regras:
 *  - so uma ONG autenticada avalia (a ONG avaliadora vem do TOKEN, nunca do corpo);
 *  - UPSERT por par ong+doador (nunca duplica; atualizar troca nota/comentario);
 *  - a cada avaliacao recalcula os agregados denormalizados no Usuario
 *    (notaMediaDoador / totalAvaliacoesDoador) e notifica o doador;
 *  - a listagem por doadorId e PUBLICA (reputacao do doador), expondo apenas
 *    ongNome, nota, comentario e data.
 */
@Service
public class AvaliacaoDoadorService {

    @Autowired
    private AvaliacaoDoadorRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ONGRepository ongRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Autowired
    private SecurityUtils security;

    @Autowired
    private InteresseRepository interesseRepository;

    @Autowired
    private AvaliacaoDoadorFotoRepository fotoRepository;

    // Teto por foto (base64): mesmo criterio das prestacoes (~2.8MB).
    private static final int MAX_FOTO_CHARS = 2_900_000;

    // Lista PUBLICA das avaliacoes recebidas por um doador (com as fotos da
    // doacao). Fotos carregadas em UMA query (evita N+1).
    public List<AvaliacaoDoadorResponseDTO> listar(Long doadorId) {
        List<AvaliacaoDoador> avals =
                repository.findByDoadorIdOrderByCriadoEmDesc(doadorId);
        if (avals.isEmpty()) return List.of();

        List<Long> ids = avals.stream()
                .map(AvaliacaoDoador::getId).collect(Collectors.toList());
        Map<Long, List<String>> fotosPorAval = fotoRepository
                .findByAvaliacaoDoadorIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(
                        AvaliacaoDoadorFoto::getAvaliacaoDoadorId,
                        Collectors.mapping(AvaliacaoDoadorFoto::getFoto,
                                Collectors.toList())));

        return avals.stream()
                .map(a -> toDTO(a, fotosPorAval.getOrDefault(a.getId(), List.of())))
                .collect(Collectors.toList());
    }

    // Cria ou atualiza a avaliacao da ONG autenticada para o doador (upsert).
    @Transactional
    public ResponseEntity<?> avaliar(AvaliacaoDoadorRequestDTO dto) {
        // So ONG autenticada avalia doadores; a identidade vem do token.
        Long ongId = security.ongIdAtual();
        if (ongId == null) {
            throw new AcessoNegadoException(
                    "Apenas uma ONG pode avaliar um doador.");
        }

        if (dto.getDoadorId() == null) {
            return erro("É obrigatório informar o doadorId");
        }
        if (dto.getNota() == null || dto.getNota() < 1 || dto.getNota() > 5) {
            return erro("A nota deve ser de 1 a 5");
        }
        if (dto.getFotos() != null) {
            if (dto.getFotos().size() > 3) {
                return erro("No máximo 3 fotos por avaliação");
            }
            for (String f : dto.getFotos()) {
                if (f != null && f.length() > MAX_FOTO_CHARS) {
                    return erro("Uma das fotos é muito grande");
                }
            }
        }

        Usuario doador = usuarioRepository.findById(dto.getDoadorId()).orElse(null);
        if (doador == null || doador.getDataExclusao() != null
                || !"DOADOR".equals(doador.getTipo())) {
            return erro("Doador não encontrado");
        }

        // AVALIACAO COM LASTRO: a ONG so avalia um doador com quem concluiu uma
        // doacao (existe um match CONCLUIDO entre os dois). Espelha a regra da
        // avaliacao de ONG e fecha o "review bombing" na reputacao do doador.
        if (!interesseRepository.existeConcluidoEntre(dto.getDoadorId(), ongId)) {
            throw new AcessoNegadoException(
                    "Você só pode avaliar um doador após concluir uma doação com ele.");
        }

        AvaliacaoDoador avaliacao = repository
                .findByOngIdAndDoadorId(ongId, dto.getDoadorId())
                .orElseGet(AvaliacaoDoador::new);

        avaliacao.setOngId(ongId);
        avaliacao.setDoadorId(dto.getDoadorId());
        avaliacao.setNota(dto.getNota());
        avaliacao.setComentario(dto.getComentario());

        AvaliacaoDoador salva = repository.save(avaliacao);

        // Fotos (opcional): a lista enviada SUBSTITUI as atuais. Null = nao mexe
        // (permite reavaliar a nota sem reenviar as fotos).
        if (dto.getFotos() != null) {
            fotoRepository.deleteByAvaliacaoDoadorId(salva.getId());
            for (String f : dto.getFotos()) {
                if (f == null || f.isBlank()) continue;
                AvaliacaoDoadorFoto foto = new AvaliacaoDoadorFoto();
                foto.setAvaliacaoDoadorId(salva.getId());
                foto.setFoto(f);
                fotoRepository.save(foto);
            }
        }

        recalcularMedia(doador);

        // Notifica o doador avaliado.
        String nomeOng = ongRepository.findById(ongId)
                .map(Ong::getNome).orElse("Uma ONG");
        notificacaoService.criar(
                doador.getId(),
                "Você foi avaliado!",
                "A ONG " + nomeOng + " avaliou você com "
                        + dto.getNota() + " estrelas",
                "MATCH");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toDTO(salva, fotosDaAvaliacao(salva.getId())));
    }

    private List<String> fotosDaAvaliacao(Long avaliacaoId) {
        return fotoRepository.findByAvaliacaoDoadorIdOrderByIdAsc(avaliacaoId)
                .stream().map(AvaliacaoDoadorFoto::getFoto)
                .collect(Collectors.toList());
    }

    // Recalcula os agregados denormalizados no Usuario (mesmo padrao da Ong).
    private void recalcularMedia(Usuario doador) {
        List<AvaliacaoDoador> todas =
                repository.findByDoadorIdOrderByCriadoEmDesc(doador.getId());
        if (todas.isEmpty()) {
            doador.setNotaMediaDoador(null);
            doador.setTotalAvaliacoesDoador(0);
        } else {
            double soma = todas.stream().mapToInt(AvaliacaoDoador::getNota).sum();
            double media = Math.round((soma / todas.size()) * 10.0) / 10.0;
            doador.setNotaMediaDoador(media);
            doador.setTotalAvaliacoesDoador(todas.size());
        }
        usuarioRepository.save(doador);
    }

    private AvaliacaoDoadorResponseDTO toDTO(AvaliacaoDoador a, List<String> fotos) {
        String ongNome = ongRepository.findById(a.getOngId())
                .map(Ong::getNome).orElse(null);
        return new AvaliacaoDoadorResponseDTO(
                ongNome,
                a.getNota(),
                a.getComentario(),
                fotos,
                a.getCriadoEm()
        );
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }
}
