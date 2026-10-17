package eduar.studymindredacao.domain.exception;

/** Senha correta, mas o e-mail da conta ainda não foi confirmado. Só é lançada depois da senha conferir. */
public class EmailNaoVerificadoException extends RuntimeException {
    public EmailNaoVerificadoException() {
        super("Confirme seu e-mail para entrar. Enviamos um link para a sua caixa de entrada.");
    }
}
