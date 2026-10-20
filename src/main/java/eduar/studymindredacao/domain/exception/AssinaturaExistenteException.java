package eduar.studymindredacao.domain.exception;

/** O aluno já tem uma assinatura que não foi encerrada (ativa, pendente de pagamento ou pausada). */
public class AssinaturaExistenteException extends RuntimeException {
    public AssinaturaExistenteException() {
        super("Você já tem uma assinatura. Para trocar o plano ou o cartão, use o gerenciamento da assinatura.");
    }
}
