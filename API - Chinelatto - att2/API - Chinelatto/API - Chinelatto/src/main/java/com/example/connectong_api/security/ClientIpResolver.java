package com.example.connectong_api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolve o IP do cliente de forma SEGURA (ponto unico usado pelo rate limiting
 * e pela auditoria).
 *
 * X-Forwarded-For (XFF) e um header enviado pelo PROPRIO cliente. Se a API
 * estiver exposta DIRETO (sem proxy reverso) — como na feira e no deploy atual —
 * confiar no XFF deixa qualquer um forjar o proprio IP e, com isso:
 *   - burlar o anti-forca-bruta do login (chave email+IP) e os limites de
 *     cadastro / esqueci-senha / cota da IA (basta variar o XFF a cada request);
 *   - falsear o IP gravado no log de auditoria.
 *
 * Por isso o DEFAULT e NAO confiar no XFF: usa sempre req.getRemoteAddr() (o IP
 * real da conexao TCP, que o cliente nao controla). Ligue
 * app.proxy.trust-forwarded-for=true SOMENTE quando a API rodar atras de um
 * proxy reverso confiavel que sobrescreve o XFF (ex.: Nginx/loadbalancer),
 * senao a protecao por IP fica contornavel.
 */
@Component
public class ClientIpResolver {

    @Value("${app.proxy.trust-forwarded-for:false}")
    private boolean confiarForwardedFor;

    /** IP do cliente, ou null se nao houver requisicao HTTP no contexto atual. */
    public String resolve() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();

            if (confiarForwardedFor) {
                String forwarded = req.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    // Primeiro IP da cadeia = cliente original (proxy confiavel).
                    return forwarded.split(",")[0].trim();
                }
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
