package eduar.studymindredacao.domain.exception;

/**
 * Troca de senha com a conta logada: a senha atual informada não confere. Separada de
 * {@link CredenciaisInvalidasException} de propósito: aqui o aluno continua logado, e o handler web responde 400
 * (não 401, que faria o front encerrar a sessão).
 */
public class SenhaAtualIncorretaException extends RuntimeException {
    public SenhaAtualIncorretaException() {
        super("Senha atual incorreta.");
    }
}
