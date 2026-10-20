package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.EstadoAssinaturaStripe;
import eduar.studymindredacao.domain.model.EventoWebhook;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Entrada do webhook do Stripe. Sem transação: valida a assinatura do evento, busca o estado ATUAL da
 * assinatura no Stripe (os eventos podem chegar fora de ordem) e só então delega a gravação, que é transacional.
 * <ul>
 *   <li>assinatura do webhook inválida: WebhookInvalidoException (400, o Stripe não repete);</li>
 *   <li>Stripe fora do ar: PagamentoIndisponivelException (503, o Stripe repete);</li>
 *   <li>dado inesperado do Stripe (status desconhecido etc.): IllegalStateException (500, o Stripe repete).</li>
 * </ul>
 */
@Service
public class ProcessarWebhookService {
    private static final Logger log = LoggerFactory.getLogger(ProcessarWebhookService.class);

    private final PagamentoGatewayPort gateway;
    private final SincronizarAssinaturaService sincronizar;

    public ProcessarWebhookService(PagamentoGatewayPort gateway, SincronizarAssinaturaService sincronizar) {
        this.gateway = gateway;
        this.sincronizar = sincronizar;
    }

    public ResultadoWebhook processar(String corpo, String assinatura) {
        EventoWebhook evento = gateway.validarEvento(corpo, assinatura);
        if (!evento.afetaAssinatura()) {
            return ResultadoWebhook.IGNORADO;
        }
        ResultadoWebhook resultado;
        try {
            Optional<EstadoAssinaturaStripe> estado = gateway.buscarAssinatura(evento.stripeSubscriptionId());
            if (estado.isEmpty()) {
                log.warn("Evento {} cita a assinatura {}, que não existe no Stripe; ignorado",
                        evento.id(), evento.stripeSubscriptionId());
                return ResultadoWebhook.IGNORADO;
            }
            resultado = sincronizar.aplicar(evento, estado.get());
        } catch (IllegalArgumentException e) {
            // Sem isto o GlobalExceptionHandler responderia 400 e o Stripe desistiria do evento
            throw new IllegalStateException("Dado inesperado do Stripe no evento " + evento.id(), e);
        }
        log.info("Webhook {} ({}): {}", evento.id(), evento.tipo(), resultado);
        return resultado;
    }
}
