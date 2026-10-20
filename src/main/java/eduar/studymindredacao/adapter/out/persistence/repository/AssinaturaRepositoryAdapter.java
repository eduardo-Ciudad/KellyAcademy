package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.mapper.AssinaturaPersistenceMapper;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AssinaturaRepositoryAdapter implements AssinaturaRepositoryPort {
    private final AssinaturaJpaRepository repository;

    public AssinaturaRepositoryAdapter(AssinaturaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Assinatura salvar(Assinatura assinatura) {
        Objects.requireNonNull(assinatura, "assinatura é obrigatória");
        var entity = AssinaturaPersistenceMapper.toEntity(assinatura);
        return AssinaturaPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Assinatura> buscarPorStripeSubscriptionId(String stripeSubscriptionId) {
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByStripeSubscriptionId(stripeSubscriptionId)
                .map(AssinaturaPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Assinatura> listarPorUsuario(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        return repository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId).stream()
                .map(AssinaturaPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> buscarStripeCustomerId(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        return repository.buscarStripeCustomerId(usuarioId);
    }

    @Override
    @Transactional
    public boolean vincularStripeCustomerId(UUID usuarioId, String stripeCustomerId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        if (stripeCustomerId == null || !stripeCustomerId.startsWith("cus_")) {
            throw new IllegalArgumentException("stripeCustomerId deve começar com cus_");
        }
        return repository.vincularStripeCustomerIdSeVazio(usuarioId, stripeCustomerId) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarUsuarioPorStripeCustomerId(String stripeCustomerId) {
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            return Optional.empty();
        }
        return repository.buscarUsuarioPorStripeCustomerId(stripeCustomerId);
    }
}