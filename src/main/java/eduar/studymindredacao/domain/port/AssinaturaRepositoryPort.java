package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.Assinatura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssinaturaRepositoryPort {
    Assinatura salvar(Assinatura assinatura);

    Optional<Assinatura> buscarPorStripeSubscriptionId(String stripeSubscriptionId);

    List<Assinatura> listarPorUsuario(UUID usuarioId);


    Optional<String> buscarStripeCustomerId(UUID usuarioId);

    boolean vincularStripeCustomerId(UUID usuarioId, String stripeCustomerId);

    Optional<UUID> buscarUsuarioPorStripeCustomerId(String stripeCustomerId);
}