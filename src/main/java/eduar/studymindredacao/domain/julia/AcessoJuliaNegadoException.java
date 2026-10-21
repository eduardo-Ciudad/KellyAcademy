package eduar.studymindredacao.domain.julia;

public class AcessoJuliaNegadoException extends RuntimeException {
    public AcessoJuliaNegadoException() {
        super("A Júlia está disponível apenas no plano Pro.");
    }
}