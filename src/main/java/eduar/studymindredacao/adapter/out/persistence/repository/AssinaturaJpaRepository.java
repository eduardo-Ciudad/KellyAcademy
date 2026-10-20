package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.entity.AssinaturaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface AssinaturaJpaRepository extends JpaRepository<AssinaturaEntity, UUID> {

    Optional<AssinaturaEntity> findByStripeSubscriptionId(String stripeSubscriptionId);

    List<AssinaturaEntity> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);


    @Query(value = "SELECT stripe_customer_id FROM usuarios WHERE id = :usuarioId", nativeQuery = true)
    Optional<String> buscarStripeCustomerId(@Param("usuarioId") UUID usuarioId);


    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE usuarios
               SET stripe_customer_id = :customerId
             WHERE id = :usuarioId
               AND stripe_customer_id IS NULL
            """, nativeQuery = true)
    int vincularStripeCustomerIdSeVazio(
            @Param("usuarioId") UUID usuarioId,
            @Param("customerId") String customerId
    );

    @Query(value = "SELECT id FROM usuarios WHERE stripe_customer_id = :customerId", nativeQuery = true)
    Optional<UUID> buscarUsuarioPorStripeCustomerId(@Param("customerId") String customerId);
}