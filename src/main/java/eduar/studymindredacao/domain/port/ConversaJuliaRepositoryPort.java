package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.julia.ConversaJulia;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ConversaJuliaRepositoryPort {

    ConversaJulia salvar(ConversaJulia conversa);

    Optional<ConversaJulia> buscarAtiva(UUID usuarioId, UUID redacaoId);

    int encerrarAtivasDoUsuario(UUID usuarioId, Instant agora);
}