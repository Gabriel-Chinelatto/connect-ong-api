package com.example.connectong_api.repository;

import com.example.connectong_api.model.Preferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PreferenciaRepository extends JpaRepository<Preferencia, Long> {

    Optional<Preferencia> findByUsuarioId(Long usuarioId);

    /**
     * Privacidade (mostrarEmail/mostrarTelefone) de TODAS as ONGs que tem conta,
     * em UMA consulta. Usado pela listagem GET /ongs para evitar N+1: antes cada
     * ONG disparava 2 consultas (achar a conta + achar as preferencias), ou seja
     * ~40 idas ao banco para 20 ONGs — o que custava ~6,9s com o banco longe do
     * servidor (backend nos EUA, MySQL da escola no Brasil).
     *
     * Devolve linhas [ongId, mostrarEmail, mostrarTelefone]. O LEFT JOIN traz
     * NULL quando a ONG ainda nao tem linha de preferencia — o chamador aplica
     * os MESMOS defaults de antes (email oculto, telefone visivel). ONGs sem
     * conta nenhuma nao aparecem aqui e tambem caem no default.
     */
    @Query(value = """
            SELECT u.ong_id, p.mostrar_email, p.mostrar_telefone
            FROM usuario u
            LEFT JOIN preferencia p ON p.usuario_id = u.id
            WHERE u.ong_id IS NOT NULL
            """, nativeQuery = true)
    List<Object[]> privacidadePorOng();
}
