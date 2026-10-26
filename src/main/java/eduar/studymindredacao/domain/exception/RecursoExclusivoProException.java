package eduar.studymindredacao.domain.exception;

import eduar.studymindredacao.domain.model.enums.RecursoPro;

import java.util.Objects;

/** O aluno do plano Grátis tentou usar um recurso que só existe no Pro. */
public class RecursoExclusivoProException extends RuntimeException {
    private final RecursoPro recurso;

    public RecursoExclusivoProException(RecursoPro recurso) {
        super("Este recurso está disponível apenas no plano Pro.");
        this.recurso = Objects.requireNonNull(recurso, "recurso é obrigatório");
    }

    public RecursoPro getRecurso() {
        return recurso;
    }
}
