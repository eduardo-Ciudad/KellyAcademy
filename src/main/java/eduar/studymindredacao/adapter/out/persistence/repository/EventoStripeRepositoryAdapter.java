package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.port.EventoStripeRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;

@Repository
public class EventoStripeRepositoryAdapter implements EventoStripeRepositoryPort {
    private final EventoStripeJpaRepository repository;

    public EventoStripeRepositoryAdapter(EventoStripeJpaRepository repository) {
        this.repository = repository;
    }


    @Override
    @Transactional
    public boolean registrarSeNovo(String eventoId, String tipo, OffsetDateTime quando) {
        Objects.requireNonNull(eventoId, "eventoId é obrigatório");
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(quando, "quando é obrigatório");
        return repository.inserirSeNovo(eventoId, tipo, quando) == 1;
    }
}