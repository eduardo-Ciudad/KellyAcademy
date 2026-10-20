package eduar.studymindredacao.domain.model;


import eduar.studymindredacao.domain.model.enums.CicloAssinatura;

import java.util.UUID;

public record SolicitacaoCheckout(UUID usuarioId, String stripeCustomerId, CicloAssinatura ciclo) {
    static final String PREFIXO_CUSTOMER = "cus_";

    public SolicitacaoCheckout {
        Validacoes.requererNaoNulo(usuarioId, "usuarioId");
        Validacoes.validarTextoObrigatorio(stripeCustomerId, "stripeCustomerId", 255);
        if (!stripeCustomerId.startsWith(PREFIXO_CUSTOMER)) {
            throw new IllegalArgumentException("stripeCustomerId deve começar com " + PREFIXO_CUSTOMER);
        }
        Validacoes.requererNaoNulo(ciclo, "ciclo");
    }
}