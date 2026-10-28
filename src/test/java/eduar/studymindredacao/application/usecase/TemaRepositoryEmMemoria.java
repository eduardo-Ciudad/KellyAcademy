package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Mesmo comportamento do TemaRepositoryAdapter, sem banco. */
class TemaRepositoryEmMemoria implements TemaRepositoryPort {
    private final Map<UUID, Tema> temas = new HashMap<>();

    @Override
    public Tema salvar(Tema tema) {
        UUID id = tema.id() != null ? tema.id() : UUID.randomUUID();
        OffsetDateTime criadoEm = tema.criadoEm() != null ? tema.criadoEm() : OffsetDateTime.now();
        var salvo = new Tema(id, tema.titulo(), tema.textosMotivadores(), tema.origem(), tema.ano(), tema.ativo(), criadoEm,
                tema.criadoPorId());
        temas.put(id, salvo);
        return salvo;
    }

    @Override
    public Optional<Tema> buscarPorId(UUID id) {
        return Optional.ofNullable(temas.get(id));
    }

    @Override
    public List<Tema> listarAtivos() {
        return temas.values().stream()
                .filter(t -> Boolean.TRUE.equals(t.ativo()))
                .filter(t -> t.origem() != OrigemTema.PERSONALIZADO)
                .toList();
    }

    @Override
    public List<Tema> listarPersonalizadosAtivosDo(UUID usuarioId) {
        return personalizadosDo(usuarioId)
                .filter(t -> Boolean.TRUE.equals(t.ativo()))
                .sorted(Comparator.comparing(Tema::criadoEm).reversed())
                .toList();
    }

    @Override
    public Optional<Tema> buscarPersonalizadoPorTitulo(UUID usuarioId, String titulo) {
        return personalizadosDo(usuarioId).filter(t -> t.titulo().equalsIgnoreCase(titulo)).findFirst();
    }

    @Override
    public long contarPersonalizadosCriadosDesde(UUID usuarioId, OffsetDateTime desde) {
        return personalizadosDo(usuarioId).filter(t -> !t.criadoEm().isBefore(desde)).count();
    }

    private java.util.stream.Stream<Tema> personalizadosDo(UUID usuarioId) {
        return temas.values().stream()
                .filter(t -> t.origem() == OrigemTema.PERSONALIZADO && usuarioId.equals(t.criadoPorId()));
    }
}
