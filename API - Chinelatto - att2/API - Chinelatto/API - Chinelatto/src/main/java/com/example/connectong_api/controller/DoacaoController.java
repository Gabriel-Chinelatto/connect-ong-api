package com.example.connectong_api.controller;

import com.example.connectong_api.model.Doacao;
import com.example.connectong_api.repository.DoacaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/doacoes")
@CrossOrigin(origins = "*")
public class DoacaoController {

    @Autowired
    private DoacaoRepository repository;

    @GetMapping
    public List<Doacao> listar(){
        return repository.findAll();
    }

    @PostMapping
    public Doacao criar(@RequestBody Doacao doacao){
        return repository.save(doacao);
    }
}