package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.LimiteDiarioAtingidoException;
import eduar.studymindredacao.domain.exception.LimiteGlobalAtingidoException;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import eduar.studymindredacao.domain.port.UsoIAGlobalRepositoryPort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;


@Component
public class ReservadorCotaIA {
    private final UsoIAGlobalRepositoryPort usoGlobal;
    private final UsoIADiarioRepositoryPort usoAluno;

    public ReservadorCotaIA(UsoIAGlobalRepositoryPort usoGlobal, UsoIADiarioRepositoryPort usoAluno) {
        this.usoGlobal = usoGlobal;
        this.usoAluno = usoAluno;
    }

    /**
     * @throws LimiteGlobalAtingidoException o teto do sistema no dia já foi atingido
     * @throws LimiteDiarioAtingidoException o aluno já usou as vagas dele no dia
     */
    public ReservaCota reservar(UUID usuarioId, RecursoIA recurso, LocalDate dia, int limiteAluno, int tetoGlobal) {
        if (!usoGlobal.reservar(recurso, dia, tetoGlobal)) {
            throw new LimiteGlobalAtingidoException(recurso);
        }

        boolean reservouAluno;
        try {
            reservouAluno = reservarDoAluno(recurso, usuarioId, dia, limiteAluno);
        } catch (RuntimeException e) {
            devolverGlobal(recurso, dia, e);
            throw e;
        }
        if (!reservouAluno) {
            var limiteAtingido = new LimiteDiarioAtingidoException(recurso, limiteAluno);
            devolverGlobal(recurso, dia, limiteAtingido);
            throw limiteAtingido;
        }
        return new ReservaCota(usuarioId, recurso, dia, usoAluno, usoGlobal);
    }

    /** Switch expression: um recurso novo no enum sem caso aqui não compila, então não fica sem cota. */
    private boolean reservarDoAluno(RecursoIA recurso, UUID usuarioId, LocalDate dia, int limite) {
        return switch (recurso) {
            case CORRECAO -> usoAluno.reservarCorrecao(usuarioId, dia, limite);
            case TRANSCRICAO -> usoAluno.reservarTranscricao(usuarioId, dia, limite);
            case JULIA -> usoAluno.reservarMensagemJulia(usuarioId, dia, limite);
        };
    }

    /** Também switch expression (um switch comum sobre enum não obriga a cobrir todos os casos). */
    static void liberarDoAluno(UsoIADiarioRepositoryPort usoAluno, RecursoIA recurso, UUID usuarioId, LocalDate dia) {
        Runnable devolver = switch (recurso) {
            case CORRECAO -> () -> usoAluno.liberarCorrecao(usuarioId, dia);
            case TRANSCRICAO -> () -> usoAluno.liberarTranscricao(usuarioId, dia);
            case JULIA -> () -> usoAluno.liberarMensagemJulia(usuarioId, dia);
        };
        devolver.run();
    }

    /** O erro original é o que importa: uma falha ao devolver vai como suprimida, sem escondê-lo. */
    private void devolverGlobal(RecursoIA recurso, LocalDate dia, RuntimeException causa) {
        try {
            usoGlobal.liberar(recurso, dia);
        } catch (RuntimeException falhaAoDevolver) {
            causa.addSuppressed(falhaAoDevolver);
        }
    }
}
