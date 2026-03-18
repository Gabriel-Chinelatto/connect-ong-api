package com.example.connectong_api.controller;

import com.example.connectong_api.model.Ong;
import com.example.connectong_api.repository.ONGRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ongs")
@CrossOrigin(origins = "*")
public class ONGController {

    @Autowired
    private ONGRepository ongRepository;

    @GetMapping
    public List<Ong> listar(@RequestParam(required = false) String nome) {
        if (nome != null && !nome.isEmpty()) {
            return ongRepository.findByNomeContainingIgnoreCase(nome);
        }
        return ongRepository.findAll();
    }

    @PostMapping
    public Ong criar(@RequestBody Ong ong) {
        return ongRepository.save(ong);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Ong> atualizar(@PathVariable Long id, @RequestBody Ong ongAtualizada) {
        return ongRepository.findById(id)
                .map(ong -> {
                    ong.setNome(ongAtualizada.getNome());
                    ong.setEmail(ongAtualizada.getEmail());
                    ong.setTelefone(ongAtualizada.getTelefone());
                    ong.setCidade(ongAtualizada.getCidade());
                    ong.setDescricao(ongAtualizada.getDescricao());
                    Ong atual = ongRepository.save(ong);
                    return ResponseEntity.ok(atual);
                }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deletar(@PathVariable Long id) {
        return ongRepository.findById(id)
                .map(ong -> {
                    ongRepository.delete(ong);
                    return ResponseEntity.noContent().build();
                }).orElse(ResponseEntity.notFound().build());
    }
}