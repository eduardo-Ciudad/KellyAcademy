package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;

import java.time.OffsetDateTime;
import java.util.UUID;


public record EstadoAssinaturaStripe(
        String stripeSubscriptionId,
        String stripeCustomerId,
        UUID usuarioIdMetadata,
        CicloAssinatura ciclo,
        StatusAssinatura status,
        OffsetDateTime periodoAtualFim,
        boolean cancelarNoFimDoPeriodo
) {
    public EstadoAssinaturaStripe {
        Validacoes.validarTextoObrigatorio(stripeSubscriptionId, "stripeSubscriptionId", 255);
        if (!stripeSubscriptionId.startsWith(Assinatura.PREFIXO_SUBSCRIPTION)) {
            throw new IllegalArgumentException("stripeSubscriptionId deve começar com " + Assinatura.PREFIXO_SUBSCRIPTION);
        }
        Validacoes.validarTextoObrigatorio(stripeCustomerId, "stripeCustomerId", 255);
        if (!stripeCustomerId.startsWith(SolicitacaoCheckout.PREFIXO_CUSTOMER)) {
            throw new IllegalArgumentException("stripeCustomerId deve começar com " + SolicitacaoCheckout.PREFIXO_CUSTOMER);
        }
        Validacoes.requererNaoNulo(ciclo, "ciclo");
        Validacoes.requererNaoNulo(status, "status");
        Validacoes.requererNaoNulo(periodoAtualFim, "periodoAtualFim");
    }
}