package eduar.studymindredacao.application.usecase;

/** O que aconteceu com um evento do Stripe. Vai para o log; todos respondem 200 (nenhum adianta repetir). */
public enum ResultadoWebhook {
    /** Assinatura gravada ou atualizada. */
    PROCESSADO,
    /** O Stripe reenviou um evento já processado. */
    REPETIDO,
    /** Evento que não mexe em assinatura, ou assinatura que não existe mais no Stripe. */
    IGNORADO,
    /** Assinatura sem aluno conhecido (criada fora do app, por exemplo direto no Dashboard). */
    SEM_DONO
}
