package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioTemaRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ListarTemasService {
    private final TemaRepositoryPort temaRepository;
    private final UsuarioTemaRepositoryPort usuarioTemaRepository;

    public ListarTemasService(TemaRepositoryPort temaRepository, UsuarioTemaRepositoryPort usuarioTemaRepository) {
        this.temaRepository = temaRepository;
        this.usuarioTemaRepository = usuarioTemaRepository;
    }

    public List<Tema> listarAtivos() {
        return temaRepository.listarAtivos();
    }

    /**
     * Os temas personalizados do aluno primeiro (mais recentes antes), depois os oficiais e autorais ativos
     * e os temas possíveis que ele adicionou à lista.
     */
    public List<Tema> listarDisponiveis(UUID usuarioId) {
        var adicionados = usuarioTemaRepository.listarTemaIds(usuarioId);
        var doSistema = temaRepository.listarAtivos().stream()
                .filter(t -> t.origem() != OrigemTema.PREVISAO || adicionados.contains(t.id()));
        return Stream.concat(temaRepository.listarPersonalizadosAtivosDo(usuarioId).stream(), doSistema).toList();
    }
}
