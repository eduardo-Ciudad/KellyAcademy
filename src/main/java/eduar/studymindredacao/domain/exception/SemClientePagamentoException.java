package eduar.studymindredacao.domain.exception;

/** O aluno nunca passou pelo checkout: não existe cliente no Stripe, então não há assinatura nem fatura para gerenciar. */
public class SemClientePagamentoException extends RuntimeException {
    public SemClientePagamentoException() {
        super("Você ainda não tem assinatura para gerenciar.");
    }
}
