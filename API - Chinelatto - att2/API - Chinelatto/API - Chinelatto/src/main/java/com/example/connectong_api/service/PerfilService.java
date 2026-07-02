package com.example.connectong_api.service;

import com.example.connectong_api.dto.AlterarSenhaDTO;
import com.example.connectong_api.dto.PerfilDTO;
import com.example.connectong_api.model.Preferencia;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.PreferenciaRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Gerencia o perfil do usuario, a troca de senha e as preferencias (aparencia,
 * acessibilidade, notificacoes, privacidade). Ao alterar a senha exige a senha
 * atual correta (conferida via BCrypt) antes de gravar o novo hash. Preferencias
 * inexistentes sao criadas com os valores padrao sob demanda.
 */
@Service
public class PerfilService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PreferenciaRepository preferenciaRepository;

    @Autowired
    private ONGRepository ongRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // ===================== PERFIL =====================
    public ResponseEntity<?> obterPerfil(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return naoEncontrado();
        return ResponseEntity.ok(toPerfilDTO(u));
    }

    public ResponseEntity<?> atualizarPerfil(Long usuarioId, PerfilDTO dto) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return naoEncontrado();

        if (dto.getNome() != null && !dto.getNome().isBlank()) {
            u.setNome(dto.getNome());
        }
        u.setTelefone(dto.getTelefone());
        u.setCidade(dto.getCidade());
        u.setEstado(dto.getEstado());
        u.setBio(dto.getBio());
        u.setFotoUrl(dto.getFotoUrl());
        // Foto da galeria (base64). So sobrescreve quando enviada, para nao
        // apagar a foto existente numa atualizacao que nao mexeu nela.
        if (dto.getFotoBase64() != null) {
            u.setFotoBase64(dto.getFotoBase64());
        }

        return ResponseEntity.ok(toPerfilDTO(usuarioRepository.save(u)));
    }

    // ===================== SENHA =====================
    public ResponseEntity<?> alterarSenha(Long usuarioId, AlterarSenhaDTO dto) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return naoEncontrado();

        if (dto.getNovaSenha() == null || dto.getNovaSenha().length() < 6) {
            return erro("A nova senha deve ter ao menos 6 caracteres");
        }
        if (!passwordEncoder.matches(dto.getSenhaAtual(), u.getSenha())) {
            return erro("Senha atual incorreta");
        }

        u.setSenha(passwordEncoder.encode(dto.getNovaSenha()));
        usuarioRepository.save(u);

        Map<String, String> ok = new HashMap<>();
        ok.put("mensagem", "Senha alterada com sucesso");
        return ResponseEntity.ok(ok);
    }

    // ===================== PREFERENCIAS =====================
    public ResponseEntity<?> obterPreferencias(Long usuarioId) {
        if (usuarioRepository.findById(usuarioId).isEmpty()) return naoEncontrado();
        Preferencia p = preferenciaRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> preferenciaRepository.save(Preferencia.padrao(usuarioId)));
        return ResponseEntity.ok(p);
    }

    public ResponseEntity<?> atualizarPreferencias(Long usuarioId, Preferencia dados) {
        if (usuarioRepository.findById(usuarioId).isEmpty()) return naoEncontrado();

        Preferencia p = preferenciaRepository.findByUsuarioId(usuarioId)
                .orElseGet(() -> Preferencia.padrao(usuarioId));

        p.setUsuarioId(usuarioId);
        p.setTema(dados.getTema());
        p.setTamanhoFonte(dados.getTamanhoFonte());
        p.setAltoContraste(dados.getAltoContraste());
        p.setFonteDislexia(dados.getFonteDislexia());
        p.setNavegacaoSimplificada(dados.getNavegacaoSimplificada());
        p.setNotifMensagens(dados.getNotifMensagens());
        p.setNotifMatch(dados.getNotifMatch());
        p.setNotifCampanhas(dados.getNotifCampanhas());
        p.setNotifNecessidades(dados.getNotifNecessidades());
        p.setNotifNoticias(dados.getNotifNoticias());
        p.setMostrarTelefone(dados.getMostrarTelefone());
        p.setMostrarEmail(dados.getMostrarEmail());
        p.setPerfilPublico(dados.getPerfilPublico());
        p.setReceberContatos(dados.getReceberContatos());
        p.setReceberSugestoes(dados.getReceberSugestoes());

        return ResponseEntity.ok(preferenciaRepository.save(p));
    }

    // ===================== EXCLUIR CONTA (soft-delete) =====================
    // Marca a conta como excluida (dataExclusao) em vez de apagar: preserva o
    // historico (doacoes, avaliacoes, matches) sem orfaos nem erro de FK, e a
    // conta deixa de logar. Se for conta de ONG, marca tambem o perfil da ONG
    // (que some das listagens/ranking/perfil publico).
    public ResponseEntity<?> excluirConta(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId).orElse(null);
        if (u == null) return naoEncontrado();

        LocalDateTime agora = LocalDateTime.now();
        u.setDataExclusao(agora);
        usuarioRepository.save(u);

        if (u.getOngId() != null) {
            ongRepository.findById(u.getOngId()).ifPresent(ong -> {
                ong.setDataExclusao(agora);
                ongRepository.save(ong);
            });
        }

        Map<String, String> ok = new HashMap<>();
        ok.put("mensagem", "Conta excluída.");
        return ResponseEntity.ok(ok);
    }

    // ===================== HELPERS =====================
    private PerfilDTO toPerfilDTO(Usuario u) {
        PerfilDTO dto = new PerfilDTO();
        dto.setId(u.getId());
        dto.setNome(u.getNome());
        dto.setEmail(u.getEmail());
        dto.setTelefone(u.getTelefone());
        dto.setCidade(u.getCidade());
        dto.setEstado(u.getEstado());
        dto.setBio(u.getBio());
        dto.setFotoUrl(u.getFotoUrl());
        dto.setFotoBase64(u.getFotoBase64());
        dto.setTipo(u.getTipo());
        dto.setOngId(u.getOngId());
        return dto;
    }

    private ResponseEntity<?> erro(String mensagem) {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", mensagem);
        return ResponseEntity.badRequest().body(erro);
    }

    private ResponseEntity<?> naoEncontrado() {
        Map<String, String> erro = new HashMap<>();
        erro.put("erro", "Usuário não encontrado");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erro);
    }
}
