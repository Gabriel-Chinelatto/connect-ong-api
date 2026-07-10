package com.example.connectong_api.service;

import com.example.connectong_api.model.AuditLog;
import com.example.connectong_api.repository.AuditLogRepository;
import com.example.connectong_api.security.ClientIpResolver;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Registro central de auditoria.
 *
 * O metodo registrar() NUNCA propaga excecao: auditoria e um efeito
 * colateral e jamais pode quebrar a operacao principal (ex.: um login
 * valido nao pode falhar so porque o log nao gravou).
 */
@Service
public class AuditService {

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private ClientIpResolver clientIpResolver;

    public void registrar(String acao, Long usuarioId, String descricao) {
        try {
            AuditLog log = new AuditLog();
            log.setAcao(acao);
            log.setUsuarioId(usuarioId);
            log.setDescricao(truncar(descricao, 500));
            log.setIp(clientIpResolver.resolve());
            repository.save(log);
        } catch (Exception ignorado) {
            // auditoria e best-effort: falha aqui nao afeta a operacao
        }
    }

    private String truncar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
