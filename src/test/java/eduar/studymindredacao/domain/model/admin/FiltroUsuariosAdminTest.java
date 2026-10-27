package eduar.studymindredacao.domain.model.admin;

import eduar.studymindredacao.domain.model.enums.Plano;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroUsuariosAdminTest {

    @Test
    void semFiltrosUsaOTamanhoPadrao() {
        var filtro = FiltroUsuariosAdmin.semFiltros(2);

        assertThat(filtro.busca()).isNull();
        assertThat(filtro.plano()).isNull();
        assertThat(filtro.emailVerificado()).isNull();
        assertThat(filtro.pagina()).isEqualTo(2);
        assertThat(filtro.tamanho()).isEqualTo(FiltroUsuariosAdmin.TAMANHO_PADRAO);
    }

    @Test
    void guardaOsFiltrosPreenchidos() {
        var filtro = new FiltroUsuariosAdmin("ana", Plano.PRO, false, 0, 10);

        assertThat(filtro.busca()).isEqualTo("ana");
        assertThat(filtro.plano()).isEqualTo(Plano.PRO);
        assertThat(filtro.emailVerificado()).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void buscaEmBrancoViraNula(String busca) {
        assertThat(new FiltroUsuariosAdmin(busca, null, null, 0, 20).busca()).isNull();
    }

    @Test
    void buscaPerdeOsEspacosDasPontas() {
        assertThat(new FiltroUsuariosAdmin("  ana silva  ", null, null, 0, 20).busca()).isEqualTo("ana silva");
    }

    @Test
    void buscaAte100CaracteresPassaE101Falha() {
        assertThat(new FiltroUsuariosAdmin("a".repeat(100), null, null, 0, 20).busca()).hasSize(100);
        assertThatThrownBy(() -> new FiltroUsuariosAdmin("a".repeat(101), null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("busca");
    }

    @Test
    void paginaNegativaFalha() {
        assertThatThrownBy(() -> new FiltroUsuariosAdmin(null, null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pagina");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 50})
    void tamanhoNosLimitesPassa(int tamanho) {
        assertThat(new FiltroUsuariosAdmin(null, null, null, 0, tamanho).tamanho()).isEqualTo(tamanho);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 51, 1000})
    void tamanhoForaDosLimitesFalha(int tamanho) {
        assertThatThrownBy(() -> new FiltroUsuariosAdmin(null, null, null, 0, tamanho))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tamanho");
    }

    @Test
    void inicioEhPaginaVezesTamanhoSemEstourarInt() {
        assertThat(new FiltroUsuariosAdmin(null, null, null, 3, 20).inicio()).isEqualTo(60);
        assertThat(new FiltroUsuariosAdmin(null, null, null, Integer.MAX_VALUE, 50).inicio())
                .isEqualTo((long) Integer.MAX_VALUE * 50);
    }
}
