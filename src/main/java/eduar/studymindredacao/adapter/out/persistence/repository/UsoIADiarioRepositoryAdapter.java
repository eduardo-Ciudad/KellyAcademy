package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.adapter.out.persistence.mapper.UsoIADiarioPersistenceMapper;
import eduar.studymindredacao.domain.model.UsoIADiario;
import eduar.studymindredacao.domain.port.UsoIADiarioRepositoryPort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UsoIADiarioRepositoryAdapter implements UsoIADiarioRepositoryPort {
    private final UsoIADiarioJpaRepository repository;
    private final UsuarioJpaRepository usuarioRepository;

    public UsoIADiarioRepositoryAdapter(
            UsoIADiarioJpaRepository repository,
            UsuarioJpaRepository usuarioRepository
    ) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional
    public UsoIADiario salvar(UsoIADiario usoIADiario) {
        var usuario = usuarioRepository.getReferenceById(usoIADiario.usuarioId());
        var entity = UsoIADiarioPersistenceMapper.toEntity(usoIADiario, usuario);
        return UsoIADiarioPersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UsoIADiario> buscarPorId(UUID id) {
        return repository.findById(id).map(UsoIADiarioPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UsoIADiario> buscarPorUsuarioIdEData(UUID usuarioId, LocalDate data) {
        return repository.findByUsuario_IdAndData(usuarioId, data)
                .map(UsoIADiarioPersistenceMapper::toDomain);
    }

    @Override
    public void excluirPorId(UUID id) {
        repository.deleteById(id);
    }

    @Override
    @Transactional
    public boolean reservarCorrecao(UUID usuarioId, LocalDate data, int limite) {
        if (limite <= 0) {
            return false;
        }
        return repository.reservarCorrecao(usuarioId, data, limite) == 1;
    }

    @Override
    @Transactional
    public void liberarCorrecao(UUID usuarioId, LocalDate data) {
        repository.liberarCorrecao(usuarioId, data);
    }

    @Override
    @Transactional
    public boolean reservarTranscricao(UUID usuarioId, LocalDate data, int limite) {
        if (limite <= 0) {
            return false;
        }
        return repository.reservarTranscricao(usuarioId, data, limite) == 1;
    }

    @Override
    @Transactional
    public void liberarTranscricao(UUID usuarioId, LocalDate data) {
        repository.liberarTranscricao(usuarioId, data);
    }

    @Override
    @Transactional
    public void registrarTokens(UUID usuarioId, LocalDate data, int tokensEntrada, int tokensSaida) {
        repository.registrarTokens(usuarioId, data, tokensEntrada, tokensSaida);
    }

    @Override
    @Transactional
    public boolean reservarMensagemJulia(UUID usuarioId, LocalDate data, int limite) {
        if (limite <= 0) {
            return false;
        }
        return repository.reservarMensagemJulia(usuarioId, data, limite) == 1;
    }

    @Override
    @Transactional
    public void liberarMensagemJulia(UUID usuarioId, LocalDate data) {
        repository.liberarMensagemJulia(usuarioId, data);
    }

    @Override
    @Transactional(readOnly = true)
    public int contarMensagensJulia(UUID usuarioId, LocalDate data) {
        return repository.contarMensagensJulia(usuarioId, data);
    }

    @Override
    @Transactional
    public boolean zerarContadores(UUID usuarioId, LocalDate data) {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        Objects.requireNonNull(data, "data é obrigatória");
        return repository.zerarContadores(usuarioId, data) > 0;
    }
}
