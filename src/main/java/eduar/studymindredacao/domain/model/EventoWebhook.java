package eduar.studymindredacao.domain.model;

public record EventoWebhook(String id, String tipo, String stripeSubscriptionId) {
    static final String PREFIXO_EVENTO = "evt_";

    public EventoWebhook {
        Validacoes.validarTextoObrigatorio(id, "id", 255);
        if (!id.startsWith(PREFIXO_EVENTO)) {
            throw new IllegalArgumentException("id do evento deve começar com " + PREFIXO_EVENTO);
        }
        Validacoes.validarTextoObrigatorio(tipo, "tipo", 100);
        if (stripeSubscriptionId != null && !stripeSubscriptionId.startsWith(Assinatura.PREFIXO_SUBSCRIPTION)) {
            throw new IllegalArgumentException("stripeSubscriptionId deve começar com " + Assinatura.PREFIXO_SUBSCRIPTION);
        }
    }

    public boolean afetaAssinatura() {
        return stripeSubscriptionId != null;
    }
}