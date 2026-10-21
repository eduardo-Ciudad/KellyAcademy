package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.mapper.ConversaJuliaPersistenceMapper;
import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.StatusConversa;
import eduar.studymindredacao.domain.port.ConversaJuliaRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversaJuliaRepositoryAdapter implements ConversaJuliaRepositoryPort {
    private final ConversaJuliaJpaRepository repository;

    public ConversaJuliaRepositoryAdapter(ConversaJuliaJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ConversaJulia salvar(ConversaJulia conversa) {
        Objects.requireNonNull(conversa, "conversa é obrigatória");
        var entity = ConversaJuliaPersistenceMapper.toEntity(conversa);
        return ConversaJuliaPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConversaJulia> buscarAtiva(UUID usuarioId, UUID redacaoId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        Objects.requireNonNull(redacaoId, "redacaoId é obrigatório");
        return repository.buscarComMensagens(usuarioId, redacaoId, StatusConversa.ATIVA)
                .map(ConversaJuliaPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public int encerrarAtivasDoUsuario(UUID usuarioId, Instant agora) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        Objects.requireNonNull(agora, "agora é obrigatório");
        return repository.encerrarAtivas(usuarioId, agora.atOffset(ZoneOffset.UTC),
                StatusConversa.ATIVA, StatusConversa.ENCERRADA);
    }
}