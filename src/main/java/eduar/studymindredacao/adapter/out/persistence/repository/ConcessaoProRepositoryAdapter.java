package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.mapper.ConcessaoProPersistenceMapper;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.port.ConcessaoProRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConcessaoProRepositoryAdapter implements ConcessaoProRepositoryPort {
    private final ConcessaoProJpaRepository repository;

    public ConcessaoProRepositoryAdapter(ConcessaoProJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Com flush na hora, de propósito: ao conceder, a vencida é fechada (UPDATE) e a nova inserida (INSERT) na mesma
     * transação. No flush o Hibernate roda os INSERTs antes dos UPDATEs, e o índice único da concessão aberta
     * barraria a nova. Também faz uma violação do índice estourar aqui, e não só no commit.
     */
    @Override
    @Transactional
    public ConcessaoPro salvar(ConcessaoPro concessao) {
        Objects.requireNonNull(concessao, "concessao é obrigatória");
        var entity = ConcessaoProPersistenceMapper.toEntity(concessao);
        return ConcessaoProPersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConcessaoPro> buscarAberta(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        return repository.findByUsuarioIdAndRevogadaEmIsNull(usuarioId)
                .map(ConcessaoProPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConcessaoPro> listarPorUsuario(UUID usuarioId) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        return repository.findByUsuarioIdOrderByInicioDesc(usuarioId).stream()
                .map(ConcessaoProPersistenceMapper::toDomain)
                .toList();
    }
}
