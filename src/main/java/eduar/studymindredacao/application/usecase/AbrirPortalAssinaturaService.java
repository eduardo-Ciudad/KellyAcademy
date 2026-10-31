package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.SemClientePagamentoException;
import eduar.studymindredacao.domain.model.SessaoPortal;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/**
 * Abre o portal do cliente do Stripe (cancelar no fim do período, trocar cartão, ver faturas).
 * Basta o aluno ter cliente no Stripe: ex-assinante também entra, para ver e baixar as faturas.
 * Nunca cria cliente: quem nunca passou pelo checkout (ex.: Pro só por concessão) não tem o que gerenciar.
 * Sem @Transactional: a chamada ao Stripe é HTTP e não deve segurar conexão do banco.
 */
@Service
public class AbrirPortalAssinaturaService {
    private final AssinaturaRepositoryPort assinaturas;
    private final PagamentoGatewayPort gateway;

    public AbrirPortalAssinaturaService(AssinaturaRepositoryPort assinaturas, PagamentoGatewayPort gateway) {
        this.assinaturas = assinaturas;
        this.gateway = gateway;
    }

    public SessaoPortal abrir(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        String cliente = assinaturas.buscarStripeCustomerId(usuarioId)
                .orElseThrow(SemClientePagamentoException::new);
        return gateway.criarSessaoPortal(cliente);
    }
}
