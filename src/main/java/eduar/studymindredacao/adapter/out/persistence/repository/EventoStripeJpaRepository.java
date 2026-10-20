package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.EventoStripeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

interface EventoStripeJpaRepository extends JpaRepository<EventoStripeEntity, String> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO eventos_stripe (id, tipo, processado_em)
            VALUES (:id, :tipo, :quando)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int inserirSeNovo(
            @Param("id") String id,
            @Param("tipo") String tipo,
            @Param("quando") OffsetDateTime quando
    );
}