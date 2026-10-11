package eduar.studymindredacao.domain.exception;

/** Repertório inexistente ou inativo: para o aluno, os dois casos são o mesmo 404. */
public class RepertorioNaoEncontradoException extends RuntimeException {
    public RepertorioNaoEncontradoException(String codigo) {
        super("Repertório não encontrado: " + codigo);
    }
}
