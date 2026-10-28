package eduar.studymindredacao.domain.exception;

/** O aluno já criou todos os temas personalizados permitidos no dia. */
public class LimiteTemasPersonalizadosException extends RuntimeException {
    private final int limite;

    public LimiteTemasPersonalizadosException(int limite) {
        super("Você já criou " + limite + " temas hoje. Tente novamente amanhã ou use um tema que já está na sua lista.");
        this.limite = limite;
    }

    public int getLimite() {
        return limite;
    }
}
