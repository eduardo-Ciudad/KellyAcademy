package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.EstadoAssinaturaStripe;
import eduar.studymindredacao.domain.model.EventoWebhook;
import eduar.studymindredacao.domain.model.SessaoCheckout;
import eduar.studymindredacao.domain.model.SolicitacaoCheckout;

import java.util.Optional;
import java.util.UUID;


public interface PagamentoGatewayPort {
    String criarCliente(UUID usuarioId, String nome, String email);

    SessaoCheckout criarSessaoCheckout(SolicitacaoCheckout solicitacao);


    EventoWebhook validarEvento(String corpo, String assinatura);

    Optional<EstadoAssinaturaStripe> buscarAssinatura(String stripeSubscriptionId);
}