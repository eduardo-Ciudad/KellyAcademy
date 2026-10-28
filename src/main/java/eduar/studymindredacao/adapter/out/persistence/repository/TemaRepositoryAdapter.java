package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.mapper.TemaPersistenceMapper;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TemaRepositoryAdapter implements TemaRepositoryPort {
    private final TemaJpaRepository repository;

    public TemaRepositoryAdapter(TemaJpaRepository repository) {
        this.repository = repository;
    }

    /** saveAndFlush: violação do índice único estoura aqui dentro, já como DataIntegrityViolationException. */
    @Override
    @Transactional
    public Tema salvar(Tema tema) {
        var entity = TemaPersistenceMapper.toEntity(tema);
        return TemaPersistenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tema> buscarPorId(UUID id) {
        return repository.findById(id).map(TemaPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tema> listarAtivos() {
        return repository.findByAtivoTrueAndOrigemNotOrderByAnoDescTituloAsc(OrigemTema.PERSONALIZADO).stream()
                .map(TemaPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Tema> listarPersonalizadosAtivosDo(UUID usuarioId) {
        return repository.findByCriadoPorIdAndOrigemAndAtivoTrueOrderByCriadoEmDesc(usuarioId, OrigemTema.PERSONALIZADO)
                .stream()
                .map(TemaPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Tema> buscarPersonalizadoPorTitulo(UUID usuarioId, String titulo) {
        return repository.buscarPersonalizadoPorTitulo(usuarioId, titulo).map(TemaPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long contarPersonalizadosCriadosDesde(UUID usuarioId, OffsetDateTime desde) {
        return repository.countByCriadoPorIdAndOrigemAndCriadoEmGreaterThanEqual(usuarioId, OrigemTema.PERSONALIZADO, desde);
    }
}
