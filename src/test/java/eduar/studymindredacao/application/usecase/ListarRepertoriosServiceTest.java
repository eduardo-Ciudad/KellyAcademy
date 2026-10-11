package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListarRepertoriosServiceTest {
    private final RepertorioRepositoryEmMemoria repertorios = new RepertorioRepositoryEmMemoria();
    private final ListarRepertoriosService service = new ListarRepertoriosService(repertorios);

    @Test
    void repassaOFiltroEDevolveAPaginaDaPorta() {
        repertorios.salvar(Repertorios.ativo("REP-025", "Zygmunt", TipoEntidade.PERSON));
        repertorios.salvar(Repertorios.ativo("REP-026", "Anomia", TipoEntidade.CONCEPT));
        var filtro = new FiltroRepertorio(null, null, TipoEntidade.PERSON, null, null, 0, 20);

        var pagina = service.listar(filtro);

        assertThat(repertorios.ultimoFiltro()).isSameAs(filtro);
        assertThat(pagina.itens()).extracting(r -> r.codigo()).containsExactly("REP-025");
        assertThat(pagina.totalItens()).isEqualTo(1);
    }

    @Test
    void naoListaInativos() {
        repertorios.salvar(Repertorios.ativo("REP-025", "Ativo", TipoEntidade.PERSON));
        repertorios.salvar(Repertorios.inativo("REP-001", "Inativo"));

        var pagina = service.listar(FiltroRepertorio.semFiltros(0));

        assertThat(pagina.itens()).extracting(r -> r.codigo()).containsExactly("REP-025");
    }
}
