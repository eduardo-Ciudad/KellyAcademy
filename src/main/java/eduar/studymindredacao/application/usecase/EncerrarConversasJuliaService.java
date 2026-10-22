package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.port.ConversaJuliaRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Logout: encerra as conversas ativas do aluno. O histórico fica guardado (análise de qualidade e custo,
 * citado na Política de Privacidade); o aluno só deixa de vê-lo.
 */
@Service
public class EncerrarConversasJuliaService {
    private final ConversaJuliaRepositoryPort conversaRepository;
    private final Clock clock;

    public EncerrarConversasJuliaService(ConversaJuliaRepositoryPort conversaRepository, Clock clock) {
        this.conversaRepository = conversaRepository;
        this.clock = clock;
    }

    /** @return quantas conversas foram encerradas (0 se não havia nenhuma ativa) */
    public int encerrar(UUID usuarioId) {
        return conversaRepository.encerrarAtivasDoUsuario(usuarioId, clock.instant());
    }
}
