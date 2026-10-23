package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.port.UsoIAGlobalRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Cada método na própria transação curta: a reserva faz commit sozinha e não segura o lock da linha
 * durante a chamada à IA. O recurso vai sempre por parâmetro (nunca concatenado) e o CHECK da V24
 * recusa qualquer valor fora do enum.
 */
@Repository
public class UsoIAGlobalRepositoryAdapter implements UsoIAGlobalRepositoryPort {
    private final UsoIAGlobalDiarioJpaRepository repository;

    public UsoIAGlobalRepositoryAdapter(UsoIAGlobalDiarioJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public boolean reservar(RecursoIA recurso, LocalDate data, int teto) {
        // Teto zerado no .env funciona como interruptor de emergência, sem nem tocar no banco
        if (teto <= 0) {
            return false;
        }
        return repository.reservar(recurso.name(), data, teto) == 1;
    }

    @Override
    @Transactional
    public void liberar(RecursoIA recurso, LocalDate data) {
        repository.liberar(recurso.name(), data);
    }

    @Override
    @Transactional(readOnly = true)
    public int quantidadeDoDia(RecursoIA recurso, LocalDate data) {
        return repository.quantidadeDoDia(recurso.name(), data);
    }
}
