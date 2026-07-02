package com.example.connectong_api.repository;

import com.example.connectong_api.model.Mensagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Long> {

    // Mensagens de um match, em ordem cronologica.
    List<Mensagem> findByInteresseIdOrderByDataEnvioAsc(Long interesseId);

    // Marca como lidas ("visto") as mensagens ainda nao lidas enviadas pelo OUTRO
    // lado do match. Chamado quando o participante abre/atualiza o chat.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Mensagem m set m.dataLeitura = :agora "
            + "where m.interesse.id = :interesseId and m.remetente = :remetente "
            + "and m.dataLeitura is null")
    int marcarLidas(@Param("interesseId") Long interesseId,
                    @Param("remetente") String remetente,
                    @Param("agora") LocalDateTime agora);
}
