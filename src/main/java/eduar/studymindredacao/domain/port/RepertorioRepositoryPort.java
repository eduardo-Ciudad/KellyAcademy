package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.Repertorio;

import java.util.Optional;

/** Repertórios (só leitura: o conteúdo entra pelas migrations de seed). Só devolve registros ativos. */
public interface RepertorioRepositoryPort {
    /**
     * Página de repertórios ativos que atendem a todos os filtros, em ordem alfabética do nome.
     * Com somenteLiberadosNoGratis, só entram os liberados no plano Grátis (ver liberadoNoGratis).
     */
    Pagina<Repertorio> buscar(FiltroRepertorio filtro, boolean somenteLiberadosNoGratis);

    /** Listagem completa (plano Pro). */
    default Pagina<Repertorio> buscar(FiltroRepertorio filtro) {
        return buscar(filtro, false);
    }

    /**
     * Se o repertório ativo com este código é um dos liberados no Grátis: um por problema social,
     * o primeiro REP pelo código (ou, sem REP no problema, o menor código). Sem problema vinculado: nunca.
     */
    boolean liberadoNoGratis(String codigo);

    /** Repertório ativo pelo código (ex.: REP-025). Inativo ou inexistente: vazio. */
    Optional<Repertorio> buscarAtivoPorCodigo(String codigo);
}
