package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.ConcessaoPro;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConcessaoProRepositoryPort {
    /** Insere uma concessão nova ou grava a revogação de uma existente. */
    ConcessaoPro salvar(ConcessaoPro concessao);

    /**
     * A concessão não revogada do aluno, vigente ou já vencida. O índice único parcial da V25 garante que há
     * no máximo uma.
     */
    Optional<ConcessaoPro> buscarAberta(UUID usuarioId);

    /** Histórico completo do aluno, da concessão mais recente para a mais antiga. */
    List<ConcessaoPro> listarPorUsuario(UUID usuarioId);
}
