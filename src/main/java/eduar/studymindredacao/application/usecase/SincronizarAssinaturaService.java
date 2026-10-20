package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.EstadoAssinaturaStripe;
import eduar.studymindredacao.domain.model.EventoWebhook;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.EventoStripeRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Grava o estado atual de uma assinatura do Stripe. Tudo numa transação só: se gravar a assinatura falhar,
 * o registro do evento (idempotência) é desfeito junto e o retry do Stripe consegue processar de novo.
 * Não chama o Stripe: o estado chega pronto do ProcessarWebhookService, para não segurar conexão durante HTTP.
 */
@Service
public class SincronizarAssinaturaService {
    private static final Logger log = LoggerFactory.getLogger(SincronizarAssinaturaService.class);

    private final EventoStripeRepositoryPort eventos;
    private final AssinaturaRepositoryPort assinaturas;
    private final UsuarioRepositoryPort usuarios;
    private final Clock clock;

    public SincronizarAssinaturaService(
            EventoStripeRepositoryPort eventos,
            AssinaturaRepositoryPort assinaturas,
            UsuarioRepositoryPort usuarios,
            Clock clock
    ) {
        this.eventos = eventos;
        this.assinaturas = assinaturas;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Transactional
    public ResultadoWebhook aplicar(EventoWebhook evento, EstadoAssinaturaStripe estado) {
        Objects.requireNonNull(evento, "evento é obrigatório");
        Objects.requireNonNull(estado, "estado é obrigatório");
        OffsetDateTime agora = OffsetDateTime.now(clock);

        if (!eventos.registrarSeNovo(evento.id(), evento.tipo(), agora)) {
            return ResultadoWebhook.REPETIDO;
        }

        Optional<UUID> dono = acharDono(estado);
        if (dono.isEmpty()) {
            // Repetir não resolve: o evento fica registrado e o caso vai para o log
            log.warn("Assinatura {} do cliente {} sem aluno conhecido; evento {} ignorado",
                    estado.stripeSubscriptionId(), estado.stripeCustomerId(), evento.id());
            return ResultadoWebhook.SEM_DONO;
        }

        var assinatura = assinaturas.buscarPorStripeSubscriptionId(estado.stripeSubscriptionId())
                .map(existente -> existente.comEstadoDoStripe(estado.ciclo(), estado.status(),
                        estado.periodoAtualFim(), estado.cancelarNoFimDoPeriodo(), agora))
                .orElseGet(() -> Assinatura.nova(dono.get(), estado.stripeSubscriptionId(), estado.ciclo(),
                        estado.status(), estado.periodoAtualFim(), estado.cancelarNoFimDoPeriodo(), agora));
        assinaturas.salvar(assinatura);
        return ResultadoWebhook.PROCESSADO;
    }

    /** Primeiro pelo cliente do Stripe gravado no aluno; se não achar, pelo usuario_id do metadata (se a conta existe). */
    private Optional<UUID> acharDono(EstadoAssinaturaStripe estado) {
        var porCliente = assinaturas.buscarUsuarioPorStripeCustomerId(estado.stripeCustomerId());
        if (porCliente.isPresent()) {
            return porCliente;
        }
        UUID doMetadata = estado.usuarioIdMetadata();
        if (doMetadata != null && usuarios.buscarPorId(doMetadata).isPresent()) {
            return Optional.of(doMetadata);
        }
        return Optional.empty();
    }
}
