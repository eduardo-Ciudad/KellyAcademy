package eduar.studymindredacao.domain.exception;

/** Um link de verificação acabou de ser enviado para esse aluno. Vira 429 com Retry-After. */
public class ReenvioMuitoRecenteException extends RuntimeException {
    private final long segundosParaTentar;

    public ReenvioMuitoRecenteException(long segundosParaTentar) {
        super("Um link de verificação foi enviado há pouco. Aguarde alguns minutos para reenviar.");
        this.segundosParaTentar = Math.max(1, segundosParaTentar);
    }

    public long getSegundosParaTentar() {
        return segundosParaTentar;
    }
}
