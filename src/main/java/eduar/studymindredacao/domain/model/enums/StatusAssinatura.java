package eduar.studymindredacao.domain.model.enums;

public enum StatusAssinatura {
    INCOMPLETE(false),
    INCOMPLETE_EXPIRED(false),
    TRIALING(true),
    ACTIVE(true),
    PAST_DUE(false),
    CANCELED(false),
    UNPAID(false),
    PAUSED(false);

    private final boolean concedeAcesso;

    StatusAssinatura(boolean concedeAcesso) {
        this.concedeAcesso = concedeAcesso;
    }

    public boolean concedeAcesso() {
        return concedeAcesso;
    }
}