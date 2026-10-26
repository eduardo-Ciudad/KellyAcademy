package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.port.PrevisaoTemaRepositoryPort;
import eduar.studymindredacao.domain.port.TemaRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioTemaRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ListarTemasPossiveisService {
    private final TemaRepositoryPort temaRepository;
    private final PrevisaoTemaRepositoryPort previsaoRepository;
    private final UsuarioTemaRepositoryPort usuarioTemaRepository;
    private final ConsultarPlanoService consultarPlano;

    public ListarTemasPossiveisService(
            TemaRepositoryPort temaRepository,
            PrevisaoTemaRepositoryPort previsaoRepository,
            UsuarioTemaRepositoryPort usuarioTemaRepository,
            ConsultarPlanoService consultarPlano
    ) {
        this.temaRepository = temaRepository;
        this.previsaoRepository = previsaoRepository;
        this.usuarioTemaRepository = usuarioTemaRepository;
        this.consultarPlano = consultarPlano;
    }

    /** Temas possíveis ativos, em ordem de ranking, marcando os que o aluno já adicionou. Só no plano Pro. */
    public List<TemaPossivel> listar(UUID usuarioId) {
        consultarPlano.exigir(usuarioId, RecursoPro.TEMAS_POSSIVEIS);
        Map<UUID, Tema> temasPrevisao = temaRepository.listarAtivos().stream()
                .filter(t -> t.origem() == OrigemTema.PREVISAO)
                .collect(Collectors.toMap(Tema::id, Function.identity()));
        var adicionados = usuarioTemaRepository.listarTemaIds(usuarioId);

        return previsaoRepository.listarPorRanking().stream()
                .filter(p -> temasPrevisao.containsKey(p.temaId()))
                .map(p -> new TemaPossivel(temasPrevisao.get(p.temaId()), p, adicionados.contains(p.temaId())))
                .toList();
    }
}
