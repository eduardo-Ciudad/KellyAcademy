package eduar.studymindredacao.domain.exception;

/** A ação do admin não faz sentido no estado atual do aluno (ex.: e-mail já verificado). Vira 409. */
public class AcaoAdminConflitoException extends RuntimeException {
    public AcaoAdminConflitoException(String mensagem) {
        super(mensagem);
    }
}
