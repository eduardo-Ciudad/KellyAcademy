package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;

import java.time.OffsetDateTime;
import java.util.UUID;


public record Assinatura(
        UUID id,
        UUID usuarioId,
        String stripeSubscriptionId,
        CicloAssinatura ciclo,
        StatusAssinatura status,
        OffsetDateTime periodoAtualFim,
        boolean cancelarNoFimDoPeriodo,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm
) {
    static final String PREFIXO_SUBSCRIPTION = "sub_";

    public Assinatura {
        Validacoes.requererNaoNulo(usuarioId, "usuarioId");
        Validacoes.validarTextoObrigatorio(stripeSubscriptionId, "stripeSubscriptionId", 255);
        if (!stripeSubscriptionId.startsWith(PREFIXO_SUBSCRIPTION)) {
            throw new IllegalArgumentException("stripeSubscriptionId deve começar com " + PREFIXO_SUBSCRIPTION);
        }
        Validacoes.requererNaoNulo(ciclo, "ciclo");
        Validacoes.requererNaoNulo(status, "status");
        Validacoes.requererNaoNulo(periodoAtualFim, "periodoAtualFim");
        Validacoes.requererNaoNulo(criadoEm, "criadoEm");
        Validacoes.requererNaoNulo(atualizadoEm, "atualizadoEm");
        if (atualizadoEm.isBefore(criadoEm)) {
            throw new IllegalArgumentException("atualizadoEm não pode ser antes de criadoEm");
        }
    }

    public static Assinatura nova(
            UUID usuarioId,
            String stripeSubscriptionId,
            CicloAssinatura ciclo,
            StatusAssinatura status,
            OffsetDateTime periodoAtualFim,
            boolean cancelarNoFimDoPeriodo,
            OffsetDateTime quando
    ) {
        return new Assinatura(null, usuarioId, stripeSubscriptionId, ciclo, status, periodoAtualFim,
                cancelarNoFimDoPeriodo, quando, quando);
    }

    public boolean daAcessoPro(OffsetDateTime agora) {
        Validacoes.requererNaoNulo(agora, "agora");
        return status.concedeAcesso() && periodoAtualFim.isAfter(agora);
    }


    public Assinatura comEstadoDoStripe(
            CicloAssinatura novoCiclo,
            StatusAssinatura novoStatus,
            OffsetDateTime novoPeriodoAtualFim,
            boolean novoCancelarNoFimDoPeriodo,
            OffsetDateTime quando
    ) {
        return new Assinatura(id, usuarioId, stripeSubscriptionId, novoCiclo, novoStatus, novoPeriodoAtualFim,
                novoCancelarNoFimDoPeriodo, criadoEm, quando);
    }
}
