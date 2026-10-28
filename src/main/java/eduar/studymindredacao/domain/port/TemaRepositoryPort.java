package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.Tema;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemaRepositoryPort {
    Tema salvar(Tema tema);

    Optional<Tema> buscarPorId(UUID id);

    /** Temas do sistema ativos. Nunca traz personalizados. */
    List<Tema> listarAtivos();

    /** Personalizados ativos do aluno, mais recentes primeiro. */
    List<Tema> listarPersonalizadosAtivosDo(UUID usuarioId);

    /**
     * Personalizado do aluno com o mesmo título, sem diferenciar maiúsculas.
     * Considera também os inativos, porque o índice único da V26 vale para eles.
     */
    Optional<Tema> buscarPersonalizadoPorTitulo(UUID usuarioId, String titulo);

    /** Quantos personalizados o aluno criou a partir de um instante (limite diário). */
    long contarPersonalizadosCriadosDesde(UUID usuarioId, OffsetDateTime desde);
}
