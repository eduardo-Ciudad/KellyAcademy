package eduar.studymindredacao.domain.model.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanoTest {

    @Test
    void gratisTemTresCorrecoesEDuasTranscricoesPorDia() {
        assertThat(Plano.GRATIS.limiteDiario(RecursoIA.CORRECAO)).isEqualTo(3);
        assertThat(Plano.GRATIS.limiteDiario(RecursoIA.TRANSCRICAO)).isEqualTo(2);
    }

    @Test
    void proTemDezCorrecoesEDezTranscricoesPorDia() {
        assertThat(Plano.PRO.limiteDiario(RecursoIA.CORRECAO)).isEqualTo(10);
        assertThat(Plano.PRO.limiteDiario(RecursoIA.TRANSCRICAO)).isEqualTo(10);
    }

    @Test
    void proNuncaTemMenosVagasQueOGratis() {
        for (RecursoIA recurso : new RecursoIA[]{RecursoIA.CORRECAO, RecursoIA.TRANSCRICAO}) {
            assertThat(Plano.PRO.limiteDiario(recurso))
                    .as(recurso.name())
                    .isGreaterThanOrEqualTo(Plano.GRATIS.limiteDiario(recurso));
        }
    }

    @ParameterizedTest
    @EnumSource(Plano.class)
    void limiteDaJuliaNaoVemDoPlano(Plano plano) {
        assertThatThrownBy(() -> plano.limiteDiario(RecursoIA.JULIA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(Plano.class)
    void limiteDiarioExigeRecurso(Plano plano) {
        assertThatThrownBy(() -> plano.limiteDiario(null)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @EnumSource(RecursoPro.class)
    void gratisNaoLiberaNenhumRecursoPro(RecursoPro recurso) {
        assertThat(Plano.GRATIS.libera(recurso)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(RecursoPro.class)
    void proLiberaTodosOsRecursos(RecursoPro recurso) {
        assertThat(Plano.PRO.libera(recurso)).isTrue();
    }

    @Test
    void liberaExigeRecurso() {
        assertThatThrownBy(() -> Plano.PRO.libera(null)).isInstanceOf(NullPointerException.class);
    }
}
