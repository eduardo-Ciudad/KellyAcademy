package eduar.studymindredacao.domain.exception;

public class WebhookInvalidoException extends RuntimeException {
    public WebhookInvalidoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}