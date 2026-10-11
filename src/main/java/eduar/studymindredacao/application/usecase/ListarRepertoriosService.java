package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.port.RepertorioRepositoryPort;
import org.springframework.stereotype.Service;

@Service
public class ListarRepertoriosService {
    private final RepertorioRepositoryPort repertorioRepository;

    public ListarRepertoriosService(RepertorioRepositoryPort repertorioRepository) {
        this.repertorioRepository = repertorioRepository;
    }

    /** Página de repertórios ativos que atendem aos filtros, em ordem alfabética do nome. */
    public Pagina<Repertorio> listar(FiltroRepertorio filtro) {
        return repertorioRepository.buscar(filtro);
    }
}
