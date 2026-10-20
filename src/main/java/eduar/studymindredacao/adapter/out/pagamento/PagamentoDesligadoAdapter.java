package eduar.studymindredacao.adapter.out.pagamento;

import eduar.studymindredacao.domain.exception.PagamentoIndisponivelException;
import eduar.studymindredacao.domain.model.EstadoAssinaturaStripe;
import eduar.studymindredacao.domain.model.EventoWebhook;
import eduar.studymindredacao.domain.model.SessaoCheckout;
import eduar.studymindredacao.domain.model.SolicitacaoCheckout;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;

import java.util.Optional;
import java.util.UUID;

public class PagamentoDesligadoAdapter implements PagamentoGatewayPort {
    static final String MENSAGEM = "Pagamento ainda não está disponível";

    @Override
    public String criarCliente(UUID usuarioId, String nome, String email) {
        throw desligado();
    }

    @Override
    public SessaoCheckout criarSessaoCheckout(SolicitacaoCheckout solicitacao) {
        throw desligado();
    }

    @Override
    public EventoWebhook validarEvento(String corpo, String assinatura) {
        throw desligado();
    }

    @Override
    public Optional<EstadoAssinaturaStripe> buscarAssinatura(String stripeSubscriptionId) {
        throw desligado();
    }

    private static PagamentoIndisponivelException desligado() {
        return new PagamentoIndisponivelException(MENSAGEM, null);
    }
}