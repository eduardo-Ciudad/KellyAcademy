package eduar.studymindredacao.domain.exception;

public class PagamentoIndisponivelException extends RuntimeException {
    public PagamentoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}