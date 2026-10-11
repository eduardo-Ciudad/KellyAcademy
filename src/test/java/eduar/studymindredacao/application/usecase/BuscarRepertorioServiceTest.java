package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RepertorioNaoEncontradoException;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuscarRepertorioServiceTest {
    private final RepertorioRepositoryEmMemoria repertorios = new RepertorioRepositoryEmMemoria();
    private final BuscarRepertorioService service = new BuscarRepertorioService(repertorios);

    @Test
    void encontraAtivoPeloCodigo() {
        repertorios.salvar(Repertorios.ativo("REP-025", "Rita Barradas Barata", TipoEntidade.PERSON));

        assertThat(service.buscar("REP-025").nome()).isEqualTo("Rita Barradas Barata");
    }

    @Test
    void aceitaCodigoEmMinusculasEComEspacos() {
        repertorios.salvar(Repertorios.ativo("REP-025", "Rita Barradas Barata", TipoEntidade.PERSON));

        assertThat(service.buscar(" rep-025 ").codigo()).isEqualTo("REP-025");
    }

    @Test
    void inativoRespondeComoInexistente() {
        repertorios.salvar(Repertorios.inativo("REP-001", "Inativo"));

        assertThatThrownBy(() -> service.buscar("REP-001"))
                .isInstanceOf(RepertorioNaoEncontradoException.class)
                .hasMessageContaining("REP-001");
    }

    @Test
    void codigoInexistente() {
        assertThatThrownBy(() -> service.buscar("REP-999"))
                .isInstanceOf(RepertorioNaoEncontradoException.class);
    }
}
