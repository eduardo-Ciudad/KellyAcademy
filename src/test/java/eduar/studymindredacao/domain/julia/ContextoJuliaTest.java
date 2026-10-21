package eduar.studymindredacao.domain.julia;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextoJuliaTest {

    @Test
    void deveOrdenarCompetenciasPeloNumero() {
        List<CompetenciaContexto> foraDeOrdem = List.of(
                competencia(3), competencia(1), competencia(5), competencia(2), competencia(4));

        ContextoJulia contexto = contexto(foraDeOrdem, null);

        assertThat(contexto.competencias())
                .extracting(CompetenciaContexto::numero)
                .containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void deveRejeitarContextoComQuatroCompetencias() {
        List<CompetenciaContexto> quatro = List.of(competencia(1), competencia(2), competencia(3), competencia(4));

        assertThatThrownBy(() -> contexto(quatro, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarCompetenciaRepetida() {
        List<CompetenciaContexto> repetidas = List.of(
                competencia(1), competencia(2), competencia(2), competencia(4), competencia(5));

        assertThatThrownBy(() -> contexto(repetidas, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repetidas");
    }

    @Test
    void deveRejeitarNotaDeCompetenciaAcimaDe200() {
        assertThatThrownBy(() -> new CompetenciaContexto(1, 201, "x", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void motivoDeAjusteEmBrancoDeveVirarNulo() {
        ContextoJulia contexto = contexto(cincoCompetencias(), "   ");

        assertThat(contexto.motivoAjusteNota()).isNull();
        assertThat(contexto.possuiAjusteNota()).isFalse();
    }

    @Test
    void deveIndicarQuandoHouveAjusteDeNota() {
        ContextoJulia contexto = contexto(cincoCompetencias(), "Nota limitada: a proposta de intervenção está incompleta.");

        assertThat(contexto.possuiAjusteNota()).isTrue();
    }

    @Test
    void problemasNaoPodemSerAlteradosPorFora() {
        List<String> problemas = new ArrayList<>(List.of("tangenciamento"));
        CompetenciaContexto competencia = new CompetenciaContexto(2, 120, "x", problemas);

        problemas.add("outro");

        assertThat(competencia.problemas()).containsExactly("tangenciamento");
    }

    private static ContextoJulia contexto(List<CompetenciaContexto> competencias, String motivoAjuste) {
        return new ContextoJulia("Tema de teste", "Texto da redação.", 600, competencias, motivoAjuste);
    }

    private static List<CompetenciaContexto> cincoCompetencias() {
        return List.of(competencia(1), competencia(2), competencia(3), competencia(4), competencia(5));
    }

    private static CompetenciaContexto competencia(int numero) {
        return new CompetenciaContexto(numero, 120, "comentário da C" + numero, List.of());
    }
}