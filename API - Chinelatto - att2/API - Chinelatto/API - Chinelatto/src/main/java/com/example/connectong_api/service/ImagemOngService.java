package com.example.connectong_api.service;

import com.example.connectong_api.repository.ONGRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Serve o LOGO e a CAPA de uma ONG como IMAGEM de verdade (bytes + content-type),
 * em vez de base64 dentro do JSON.
 *
 * Por que existe: a listagem devolve TODAS as ONGs de uma vez (hoje 2.000). Com a
 * demonstracao ilustrada, embutir uma capa por ONG levaria a resposta de 2,4 MB
 * para dezenas de MB — e o navegador baixaria 2.000 imagens para mostrar 12
 * cards. Com URL, cada card pede a sua imagem so quando aparece na tela
 * (loading="lazy"), e o navegador guarda em cache pelo tempo do Cache-Control.
 *
 * O banco continua guardando data-URI ("data:image/jpeg;base64,..."), que e o
 * formato que o painel da ONG grava e que o perfil detalhado entrega embutido.
 * Aqui esse texto e desmontado de volta em bytes.
 */
@Service
public class ImagemOngService {

    @Autowired private ONGRepository repository;

    /** Cache do navegador: as imagens da demonstracao praticamente nao mudam. */
    private static final Duration CACHE = Duration.ofHours(12);

    public ResponseEntity<byte[]> logo(Long ongId) {
        return responder(primeiro(repository.logoBase64Da(ongId)));
    }

    public ResponseEntity<byte[]> capa(Long ongId) {
        return responder(primeiro(repository.capaBase64Da(ongId)));
    }

    private static String primeiro(List<String> valores) {
        return (valores == null || valores.isEmpty()) ? null : valores.get(0);
    }

    /**
     * Transforma o data-URI guardado no banco em resposta de imagem. Qualquer
     * conteudo ausente ou malformado vira 404 — o front ja sabe se virar sem a
     * imagem (cai na inicial do nome / no gradiente), entao 404 e melhor do que
     * derrubar a tela com erro.
     */
    private ResponseEntity<byte[]> responder(String dataUri) {
        if (dataUri == null || dataUri.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        String tipo = "image/jpeg";
        String base64 = dataUri;
        if (dataUri.startsWith("data:")) {
            int virgula = dataUri.indexOf(',');
            if (virgula < 0) {
                return ResponseEntity.notFound().build();
            }
            String cabecalho = dataUri.substring(5, virgula);   // ex.: image/png;base64
            int ponto = cabecalho.indexOf(';');
            if (ponto > 0) {
                cabecalho = cabecalho.substring(0, ponto);
            }
            if (!cabecalho.isBlank()) {
                tipo = cabecalho;
            }
            base64 = dataUri.substring(virgula + 1);
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        if (bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }

        MediaType mime;
        try {
            mime = MediaType.parseMediaType(tipo);
        } catch (Exception e) {
            mime = MediaType.IMAGE_JPEG;
        }
        return ResponseEntity.ok()
                .contentType(mime)
                .cacheControl(CacheControl.maxAge(CACHE).cachePublic())
                .body(bytes);
    }
}
