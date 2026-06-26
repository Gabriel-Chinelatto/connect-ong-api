package com.example.connectong_api.service;

import com.example.connectong_api.model.AuditLog;
import com.example.connectong_api.repository.AuditLogRepository;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    public void registrar(String acao, Long usuarioId, String descricao) {
        try {
            AuditLog log = new AuditLog();
            log.setAcao(acao);
            log.setUsuarioId(usuarioId);
            log.setDescricao(truncar(descricao, 500));
            log.setIp(ipDaRequisicao());
            repository.save(log);
        } catch (Exception ignorado) {
            // auditoria e best-effort: falha aqui nao afeta a operacao
        }
    }

    private String truncar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    private String ipDaRequisicao() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
