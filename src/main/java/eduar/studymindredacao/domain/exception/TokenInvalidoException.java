package eduar.studymindredacao.domain.exception;

/**
 * Link inexistente, expirado ou já usado. A mensagem é a mesma nos três casos de propósito:
 * quem testa tokens não descobre qual deles existe.
 */
public class TokenInvalidoException extends RuntimeException {
    public TokenInvalidoException() {
        super("Link inválido ou expirado. Peça um novo link.");
    }
}
