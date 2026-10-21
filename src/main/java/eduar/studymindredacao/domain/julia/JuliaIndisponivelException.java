package eduar.studymindredacao.domain.julia;

public class JuliaIndisponivelException extends RuntimeException {
    public JuliaIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public JuliaIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}