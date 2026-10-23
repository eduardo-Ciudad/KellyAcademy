package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsoIAGlobalRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;


public final class ReservaCota {
    private static final Logger log = LoggerFactory.getLogger(ReservaCota.class);

    private final UUID usuarioId;
    private final RecursoIA recurso;
    private final LocalDate dia;
    private final UsoIADiarioRepositoryPort usoAluno;
    private final UsoIAGlobalRepositoryPort usoGlobal;
    private final AtomicBoolean liberada = new AtomicBoolean(false);

    ReservaCota(UUID usuarioId, RecursoIA recurso, LocalDate dia,
                UsoIADiarioRepositoryPort usoAluno, UsoIAGlobalRepositoryPort usoGlobal) {
        this.usuarioId = usuarioId;
        this.recurso = recurso;
        this.dia = dia;
        this.usoAluno = usoAluno;
        this.usoGlobal = usoGlobal;
    }

    /** Devolve as duas vagas. Só a primeira chamada tem efeito; as seguintes não fazem nada. */
    public void liberar() {
        if (!liberada.compareAndSet(false, true)) {
            return;
        }
        try {
            ReservadorCotaIA.liberarDoAluno(usoAluno, recurso, usuarioId, dia);
        } catch (RuntimeException e) {
            log.warn("Não foi possível devolver a vaga do aluno ({} em {}): {}",
                    recurso, dia, LogSeguro.limpar(e.getMessage()));
        }
        try {
            usoGlobal.liberar(recurso, dia);
        } catch (RuntimeException e) {
            log.warn("Não foi possível devolver a vaga global ({} em {}): {}",
                    recurso, dia, LogSeguro.limpar(e.getMessage()));
        }
    }

    public RecursoIA recurso() {
        return recurso;
    }

    public LocalDate dia() {
        return dia;
    }
}
