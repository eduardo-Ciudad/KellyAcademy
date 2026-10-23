package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.UsoIADiarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

interface UsoIADiarioJpaRepository extends JpaRepository<UsoIADiarioEntity, UUID> {
    Optional<UsoIADiarioEntity> findByUsuario_IdAndData(UUID usuarioId, LocalDate data);


    @Modifying
    @Query(value = """
            INSERT INTO uso_ia_diario (usuario_id, data, qtd_correcoes)
            VALUES (:usuarioId, :data, 1)
            ON CONFLICT (usuario_id, data) DO UPDATE
                SET qtd_correcoes = uso_ia_diario.qtd_correcoes + 1
                WHERE uso_ia_diario.qtd_correcoes < :limite
            """, nativeQuery = true)
    int reservarCorrecao(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data, @Param("limite") int limite);

    @Modifying
    @Query(value = """
            UPDATE uso_ia_diario
               SET qtd_correcoes = GREATEST(qtd_correcoes - 1, 0)
             WHERE usuario_id = :usuarioId AND data = :data
            """, nativeQuery = true)
    int liberarCorrecao(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data);

    @Modifying
    @Query(value = """
            UPDATE uso_ia_diario
               SET tokens_entrada = tokens_entrada + :tokensEntrada,
                   tokens_saida = tokens_saida + :tokensSaida
             WHERE usuario_id = :usuarioId AND data = :data
            """, nativeQuery = true)
    int registrarTokens(
            @Param("usuarioId") UUID usuarioId,
            @Param("data") LocalDate data,
            @Param("tokensEntrada") int tokensEntrada,
            @Param("tokensSaida") int tokensSaida
    );

    @Modifying
    @Query(value = """
            INSERT INTO uso_ia_diario (usuario_id, data, qtd_transcricoes)
            VALUES (:usuarioId, :data, 1)
            ON CONFLICT (usuario_id, data) DO UPDATE
                SET qtd_transcricoes = uso_ia_diario.qtd_transcricoes + 1
                WHERE uso_ia_diario.qtd_transcricoes < :limite
            """, nativeQuery = true)
    int reservarTranscricao(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data, @Param("limite") int limite);

    @Modifying
    @Query(value = """
            UPDATE uso_ia_diario
               SET qtd_transcricoes = GREATEST(qtd_transcricoes - 1, 0)
             WHERE usuario_id = :usuarioId AND data = :data
            """, nativeQuery = true)
    int liberarTranscricao(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data);

    @Modifying
    @Query(value = """
            INSERT INTO uso_ia_diario (usuario_id, data, qtd_mensagens_julia)
            VALUES (:usuarioId, :data, 1)
            ON CONFLICT (usuario_id, data) DO UPDATE
                SET qtd_mensagens_julia = uso_ia_diario.qtd_mensagens_julia + 1
                WHERE uso_ia_diario.qtd_mensagens_julia < :limite
            """, nativeQuery = true)
    int reservarMensagemJulia(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data, @Param("limite") int limite);

    @Modifying
    @Query(value = """
            UPDATE uso_ia_diario
               SET qtd_mensagens_julia = GREATEST(qtd_mensagens_julia - 1, 0)
             WHERE usuario_id = :usuarioId AND data = :data
            """, nativeQuery = true)
    int liberarMensagemJulia(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data);

    @Query(value = """
            SELECT COALESCE(MAX(qtd_mensagens_julia), 0)
              FROM uso_ia_diario
             WHERE usuario_id = :usuarioId AND data = :data
            """, nativeQuery = true)
    int contarMensagensJulia(@Param("usuarioId") UUID usuarioId, @Param("data") LocalDate data);
}
