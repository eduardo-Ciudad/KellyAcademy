package eduar.studymindredacao.domain.exception;

/**
 * A concessão manual de Pro não pode seguir no estado atual (ex.: já foi revogada, o aluno já é Pro, não há
 * concessão ativa para revogar). Vira 409 na API.
 */
public class ConcessaoProConflitoException extends RuntimeException {
    public ConcessaoProConflitoException(String mensagem) {
        super(mensagem);
    }
}
