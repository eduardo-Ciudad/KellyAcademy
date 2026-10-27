package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.ConcessaoProEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ConcessaoProJpaRepository extends JpaRepository<ConcessaoProEntity, UUID> {

    Optional<ConcessaoProEntity> findByUsuarioIdAndRevogadaEmIsNull(UUID usuarioId);

    List<ConcessaoProEntity> findByUsuarioIdOrderByInicioDesc(UUID usuarioId);
}
