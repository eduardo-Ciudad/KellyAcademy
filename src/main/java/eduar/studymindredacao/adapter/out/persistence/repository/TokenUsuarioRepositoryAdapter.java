package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.mapper.TokenUsuarioPersistenceMapper;
import eduar.studymindredacao.domain.model.TokenUsuario;
import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import eduar.studymindredacao.domain.port.TokenUsuarioRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TokenUsuarioRepositoryAdapter implements TokenUsuarioRepositoryPort {
    private final TokenUsuarioJpaRepository repository;

    public TokenUsuarioRepositoryAdapter(TokenUsuarioJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TokenUsuario salvar(TokenUsuario token) {
        var entity = TokenUsuarioPersistenceMapper.toEntity(token);
        return TokenUsuarioPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional
    public Optional<UUID> consumirValido(String tokenHash, TipoTokenUsuario tipo, OffsetDateTime agora) {
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(agora, "agora é obrigatório");
        if (tokenHash == null || tokenHash.isBlank()) {
            return Optional.empty();
        }
        if (repository.marcarComoUsadoSeValido(tokenHash, tipo.name(), agora) != 1) {
            return Optional.empty();
        }
        // token_hash é UNIQUE: a linha que acabou de ser marcada é a única com esse hash
        return repository.buscarUsuarioIdPorHash(tokenHash);
    }

    @Override
    @Transactional
    public int invalidarAtivos(UUID usuarioId, TipoTokenUsuario tipo, OffsetDateTime agora) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        Objects.requireNonNull(tipo, "tipo é obrigatório");
        Objects.requireNonNull(agora, "agora é obrigatório");
        return repository.invalidarAtivos(usuarioId, tipo.name(), agora);
    }

    @Override
    @Transactional
    public int excluirAntigos(OffsetDateTime corte) {
        Objects.requireNonNull(corte, "corte é obrigatório");
        return repository.excluirAntigos(corte);
    }
}
