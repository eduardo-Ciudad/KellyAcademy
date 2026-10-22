package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.LimitesJulia;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaJuliaTest {

    @Test
    void deveMontarOsLimitesDaConversaAPartirDosNumeros() {
        PoliticaJulia politica = PoliticaJulia.de(10, 500, 6, 24, 20, 100);

        assertThat(politica.limites()).isEqualTo(new LimitesJulia(10, 500, 6, Duration.ofHours(24)));
        assertThat(politica.limiteDiario()).isEqualTo(20);
        assertThat(politica.tetoGlobal()).isEqualTo(100);
    }

    @Test
    void limiteDiarioETetoZeroSaoPermitidosParaDesligarAJulia() {
        PoliticaJulia politica = PoliticaJulia.de(10, 500, 6, 24, 0, 0);

        assertThat(politica.limiteDiario()).isZero();
        assertThat(politica.tetoGlobal()).isZero();
    }

    @Test
    void deveRejeitarLimiteDiarioNegativo() {
        assertThatThrownBy(() -> PoliticaJulia.de(10, 500, 6, 24, -1, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarTetoGlobalNegativo() {
        assertThatThrownBy(() -> PoliticaJulia.de(10, 500, 6, 24, 20, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void limitesIncoerentesDaConversaTambemSaoRecusados() {
        assertThatThrownBy(() -> PoliticaJulia.de(10, 500, 5, 24, 20, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoliticaJulia.de(10, 500, 6, 0, 20, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarLimitesNulos() {
        assertThatThrownBy(() -> new PoliticaJulia(null, 20, 100))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void restantesHojeDescontaOQueJaFoiUsadoENuncaFicaNegativo() {
        PoliticaJulia politica = PoliticaJulia.de(10, 500, 6, 24, 20, 100);

        assertThat(politica.restantesHoje(0)).isEqualTo(20);
        assertThat(politica.restantesHoje(7)).isEqualTo(13);
        assertThat(politica.restantesHoje(20)).isZero();
        assertThat(politica.restantesHoje(25)).isZero();
    }
}
