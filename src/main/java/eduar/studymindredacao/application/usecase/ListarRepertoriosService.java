package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.port.RepertorioRepositoryPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ListarRepertoriosService {
    private final RepertorioRepositoryPort repertorioRepository;
    private final ConsultarPlanoService consultarPlano;

    public ListarRepertoriosService(RepertorioRepositoryPort repertorioRepository, ConsultarPlanoService consultarPlano) {
        this.repertorioRepository = repertorioRepository;
        this.consultarPlano = consultarPlano;
    }

    /**
     * Página de repertórios ativos que atendem aos filtros, em ordem alfabética do nome.
     * No Grátis, só os liberados (um por problema social), com a página marcada como limitada.
     */
    public ListagemRepertorios listar(UUID usuarioId, FiltroRepertorio filtro) {
        boolean completo = consultarPlano.doUsuario(usuarioId).libera(RecursoPro.REPERTORIO_COMPLETO);
        return new ListagemRepertorios(repertorioRepository.buscar(filtro, !completo), !completo);
    }
}
