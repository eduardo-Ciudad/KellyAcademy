package eduar.studymindredacao.domain.exception;

/** Conta inexistente (ex.: id errado na URL do painel admin). Vira 404. */
public class UsuarioNaoEncontradoException extends RuntimeException {
    public UsuarioNaoEncontradoException() {
        super("Usuário não encontrado.");
    }
}
