package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.mapper.UsuarioPersistenceMapper;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UsuarioRepositoryAdapter implements UsuarioRepositoryPort {
    private final UsuarioJpaRepository repository;

    public UsuarioRepositoryAdapter(UsuarioJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Usuario salvar(Usuario usuario) {
        var entity = UsuarioPersistenceMapper.toEntity(usuario);
        return UsuarioPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorId(UUID id) {
        return repository.findById(id).map(UsuarioPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> buscarPorEmail(String email) {
        return repository.findByEmail(email.trim().toLowerCase()).map(UsuarioPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existePorEmail(String email) {
        return repository.existsByEmail(email.trim().toLowerCase());
    }

    @Override
    public void excluirPorId(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public Optional<Usuario> alterarSenha(UUID id, String novoSenhaHash, OffsetDateTime quando) {
        Objects.requireNonNull(id, "id é obrigatório");
        Objects.requireNonNull(quando, "quando é obrigatório");
        if (novoSenhaHash == null || novoSenhaHash.isBlank()) {
            throw new IllegalArgumentException("novoSenhaHash é obrigatório");
        }
        if (repository.alterarSenha(id, novoSenhaHash, quando) == 0) {
            return Optional.empty();
        }
        // clearAutomatically no UPDATE: o findById lê do banco, já com a versão nova
        return repository.findById(id).map(UsuarioPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> buscarVersaoSessao(UUID id) {
        return id == null ? Optional.empty() : repository.buscarVersaoSessao(id);
    }
}
