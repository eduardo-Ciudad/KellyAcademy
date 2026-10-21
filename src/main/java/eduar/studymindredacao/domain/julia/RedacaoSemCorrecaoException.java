package eduar.studymindredacao.domain.julia;


public class RedacaoSemCorrecaoException extends RuntimeException {
    public RedacaoSemCorrecaoException() {
        super("A Júlia só pode explicar redações que já foram corrigidas.");
    }
}