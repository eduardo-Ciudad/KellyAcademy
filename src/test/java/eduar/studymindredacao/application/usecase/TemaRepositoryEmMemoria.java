package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class TemaRepositoryEmMemoria implements TemaRepositoryPort {
    private final Map<UUID, Tema> temas = new HashMap<>();

    @Override
    public Tema salvar(Tema tema) {
        UUID id = tema.id() != null ? tema.id() : UUID.randomUUID();
        var salvo = new Tema(id, tema.titulo(), tema.textosMotivadores(), tema.origem(), tema.ano(), tema.ativo(), tema.criadoEm(),
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
        return temas.values().stream().filter(t -> Boolean.TRUE.equals(t.ativo())).toList();
    }
}
