package eduar.studymindredacao.domain.port;

import java.time.OffsetDateTime;

public interface EventoStripeRepositoryPort {

    boolean registrarSeNovo(String eventoId, String tipo, OffsetDateTime quando);
}