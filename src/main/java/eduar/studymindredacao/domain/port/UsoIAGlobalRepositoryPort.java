package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.enums.RecursoIA;

import java.time.LocalDate;

/**
 * Teto global de uso da IA por dia e por recurso, somando todos os alunos (KELLY-SEC-002).
 * Separado do {@link UsoIADiarioRepositoryPort}, que é o uso de um aluno: o teto global é do sistema.
 */
public interface UsoIAGlobalRepositoryPort {

    /**
     * Reserva 1 vaga do teto global do dia, de forma atômica no banco.
     *
     * @return false se o teto já foi atingido (ou se {@code teto <= 0})
     */
    boolean reservar(RecursoIA recurso, LocalDate data, int teto);

    /** Devolve 1 vaga reservada; o contador nunca fica negativo. */
    void liberar(RecursoIA recurso, LocalDate data);

    /**
     * Quantas vagas o recurso usou no dia (0 se ainda não há linha).
     * Só para testes e exibição: NUNCA para decidir uma reserva. Ler e depois reservar em duas
     * operações é exatamente a falha de concorrência do SEC-002; quem decide é {@link #reservar}.
     */
    int quantidadeDoDia(RecursoIA recurso, LocalDate data);
}
