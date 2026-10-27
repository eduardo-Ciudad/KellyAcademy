package eduar.studymindredacao.domain.exception;

/**
 * Quem pediu a ação não é ADMIN no banco (mesmo que o JWT diga que é: a role do token pode estar desatualizada
 * até ele expirar). Vira 403.
 */
public class AcessoAdminNegadoException extends RuntimeException {
    public AcessoAdminNegadoException() {
        super("Acesso restrito a administradores.");
    }
}
