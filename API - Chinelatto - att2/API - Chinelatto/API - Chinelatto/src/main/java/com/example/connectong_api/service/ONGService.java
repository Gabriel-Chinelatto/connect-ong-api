package com.example.connectong_api.service;

import com.example.connectong_api.dto.OngRegistroDTO;
import com.example.connectong_api.dto.OngResponseDTO;
import com.example.connectong_api.dto.UsuarioResponseDTO;
import com.example.connectong_api.model.Ong;
import com.example.connectong_api.model.Usuario;
import com.example.connectong_api.repository.ONGRepository;
import com.example.connectong_api.repository.UsuarioRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ONGService {

    @Autowired
    private ONGRepository repository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // =========================
    // CADASTRO (cria o perfil da ONG + a conta de login, ja vinculados)
    // =========================
    public ResponseEntity<?> registrar(OngRegistroDTO dto) {

        if (dto.getNome() == null || dto.getNome().isBlank()) {
            return erro("Nome da ONG é obrigatório");
        }
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            return erro("Email é obrigatório");
        }
        if (dto.getSenha() == null || dto.getSenha().isBlank()) {
            return erro("Senha é obrigatória");
        }

        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            return erro("Email já cadastrado");
        }

        // 1) cria o perfil da ONG
        Ong ong = new Ong(
                dto.getNome(),
                dto.getEmail(),
                dto.getTelefone(),
                dto.getCidade(),
                dto.getDescricao()
        );
        Ong ongSalva = repository.save(ong);

        // 2) cria a conta de login (Usuario tipo ONG) vinculada ao perfil
        Usuario usuario = new Usuario();
        usuario.setNome(dto.getNome());
        usuario.setEmail(dto.getEmail());
        usuario.setSenha(passwordEncoder.encode(dto.getSenha()));
        usuario.setTipo("ONG");
        usuario.setOngId(ongSalva.getId());
        Usuario usuarioSalvo = usuarioRepository.save(usuario);

        UsuarioResponseDTO resposta = new UsuarioResponseDTO(
                usuarioSalvo.getId(),
                usuarioSalvo.getNome(),
                usuarioSalvo.getEmail(),
                usuarioSalvo.getTipo(),
                usuarioSalvo.getOngId()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }

    // =========================
    // LISTAR
    // =========================
    public List<OngResponseDTO> listar(
            String nome
    ) {

        List<Ong> lista;

        if (nome != null &&
                !nome.isEmpty()) {

            lista =
                    repository.findByNomeContainingIgnoreCase(nome);

        } else {

            lista =
                    repository.findAll();
        }

        return lista.stream()
                .map(ong -> new OngResponseDTO(
                        ong.getId(),
                        ong.getNome(),
                        ong.getEmail(),
                        ong.getTelefone(),
                        ong.getCidade(),
                        ong.getDescricao()
                ))
                .collect(Collectors.toList());
    }

    // =========================
    // CRIAR
    // =========================
    public ResponseEntity<?> criar(
            Ong ong
    ) {

        // valida nome
        if (ong.getNome() == null ||
                ong.getNome().isEmpty()) {

            return erro(
                    "Nome da ONG é obrigatório"
            );
        }

        // valida email
        if (ong.getEmail() == null ||
                ong.getEmail().isEmpty()) {

            return erro(
                    "Email é obrigatório"
            );
        }

        Ong nova =
                repository.save(ong);

        OngResponseDTO resposta =
                new OngResponseDTO(
                        nova.getId(),
                        nova.getNome(),
                        nova.getEmail(),
                        nova.getTelefone(),
                        nova.getCidade(),
                        nova.getDescricao()
                );

        return ResponseEntity.ok(
                resposta
        );
    }

    // =========================
    // ATUALIZAR
    // =========================
    public ResponseEntity<?> atualizar(
            Long id,
            Ong ongAtualizada
    ) {

        return repository.findById(id)
                .map(ong -> {

                    ong.setNome(
                            ongAtualizada.getNome()
                    );

                    ong.setEmail(
                            ongAtualizada.getEmail()
                    );

                    ong.setTelefone(
                            ongAtualizada.getTelefone()
                    );

                    ong.setCidade(
                            ongAtualizada.getCidade()
                    );

                    ong.setDescricao(
                            ongAtualizada.getDescricao()
                    );

                    Ong atualizada =
                            repository.save(ong);

                    OngResponseDTO resposta =
                            new OngResponseDTO(
                                    atualizada.getId(),
                                    atualizada.getNome(),
                                    atualizada.getEmail(),
                                    atualizada.getTelefone(),
                                    atualizada.getCidade(),
                                    atualizada.getDescricao()
                            );

                    return ResponseEntity.ok(
                            resposta
                    );

                }).orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // =========================
    // DELETAR
    // =========================
    public ResponseEntity<?> deletar(
            Long id
    ) {

        return repository.findById(id)
                .map(ong -> {

                    repository.delete(ong);

                    return ResponseEntity
                            .noContent()
                            .build();

                }).orElse(
                        ResponseEntity.notFound().build()
                );
    }

    // =========================
    // ERRO PADRÃO
    // =========================
    private ResponseEntity<?> erro(
            String mensagem
    ) {

        Map<String, String> erro =
                new HashMap<>();

        erro.put("erro", mensagem);

        return ResponseEntity
                .badRequest()
                .body(erro);
    }
}