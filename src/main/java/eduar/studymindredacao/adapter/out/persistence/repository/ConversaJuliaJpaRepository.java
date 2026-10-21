package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.ConversaJuliaEntity;
import eduar.studymindredacao.domain.julia.StatusConversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

interface ConversaJuliaJpaRepository extends JpaRepository<ConversaJuliaEntity, UUID> {

    @Query("""
            select c from ConversaJuliaEntity c
            left join fetch c.mensagens
            where c.usuarioId = :usuarioId
              and c.redacaoId = :redacaoId
              and c.status = :status
            """)
    Optional<ConversaJuliaEntity> buscarComMensagens(
            @Param("usuarioId") UUID usuarioId,
            @Param("redacaoId") UUID redacaoId,
            @Param("status") StatusConversa status
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ConversaJuliaEntity c
               set c.status = :encerrada,
                   c.encerradaEm = :agora
             where c.usuarioId = :usuarioId
               and c.status = :ativa
            """)
    int encerrarAtivas(
            @Param("usuarioId") UUID usuarioId,
            @Param("agora") OffsetDateTime agora,
            @Param("ativa") StatusConversa ativa,
            @Param("encerrada") StatusConversa encerrada
    );
}