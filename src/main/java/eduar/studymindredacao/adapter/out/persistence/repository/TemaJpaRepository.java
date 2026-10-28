package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.TemaEntity;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TemaJpaRepository extends JpaRepository<TemaEntity, UUID> {
    List<TemaEntity> findByAtivoTrueAndOrigemNotOrderByAnoDescTituloAsc(OrigemTema origem);

    List<TemaEntity> findByCriadoPorIdAndOrigemAndAtivoTrueOrderByCriadoEmDesc(UUID criadoPorId, OrigemTema origem);

    /** Mesma expressão do índice uq_temas_personalizado_titulo (V26), para o Postgres poder usá-lo. */
    @Query("""
            select t from TemaEntity t
             where t.criadoPorId = :usuarioId
               and t.origem = eduar.studymindredacao.domain.model.enums.OrigemTema.PERSONALIZADO
               and lower(t.titulo) = lower(:titulo)
            """)
    Optional<TemaEntity> buscarPersonalizadoPorTitulo(@Param("usuarioId") UUID usuarioId, @Param("titulo") String titulo);

    long countByCriadoPorIdAndOrigemAndCriadoEmGreaterThanEqual(UUID criadoPorId, OrigemTema origem, OffsetDateTime desde);
}
