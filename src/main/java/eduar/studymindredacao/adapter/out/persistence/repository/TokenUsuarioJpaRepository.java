package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.TokenUsuarioEntity;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

interface TokenUsuarioJpaRepository extends JpaRepository<TokenUsuarioEntity, UUID> {

    /**
     * Marca como usado só se ainda serve. O UPDATE trava a linha: numa corrida, o segundo pedido reavalia
     * "usado_em IS NULL" depois do primeiro e afeta 0 linhas. Retorna 1 (consumiu) ou 0 (não serve).
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE tokens_usuario
               SET usado_em = :agora
             WHERE token_hash = :tokenHash
               AND tipo = :tipo
               AND usado_em IS NULL
               AND expira_em > :agora
            """, nativeQuery = true)
    int marcarComoUsadoSeValido(
            @Param("tokenHash") String tokenHash,
            @Param("tipo") String tipo,
            @Param("agora") OffsetDateTime agora
    );

    @Query(value = "SELECT usuario_id FROM tokens_usuario WHERE token_hash = :tokenHash", nativeQuery = true)
    Optional<UUID> buscarUsuarioIdPorHash(@Param("tokenHash") String tokenHash);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE tokens_usuario
               SET usado_em = :agora
             WHERE usuario_id = :usuarioId
               AND tipo = :tipo
               AND usado_em IS NULL
               AND expira_em > :agora
            """, nativeQuery = true)
    int invalidarAtivos(
            @Param("usuarioId") UUID usuarioId,
            @Param("tipo") String tipo,
            @Param("agora") OffsetDateTime agora
    );

    // JPQL (não nativo): o tipo do max vem da entity, OffsetDateTime, sem depender do que o driver devolve
    @Query("SELECT max(t.criadoEm) FROM TokenUsuarioEntity t WHERE t.usuarioId = :usuarioId AND t.tipo = :tipo")
    Optional<OffsetDateTime> ultimaEmissao(@Param("usuarioId") UUID usuarioId, @Param("tipo") TipoTokenUsuario tipo);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "DELETE FROM tokens_usuario WHERE expira_em < :corte OR usado_em < :corte", nativeQuery = true)
    int excluirAntigos(@Param("corte") OffsetDateTime corte);
}
