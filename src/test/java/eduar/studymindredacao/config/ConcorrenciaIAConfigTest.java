package eduar.studymindredacao.config;

import eduar.studymindredacao.domain.exception.IAOcupadaException;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcorrenciaIAConfigTest {

    @Test
    void criaOLimitadorComOsValoresDasProperties() {
        var limitador = new ConcorrenciaIAConfig().limitadorConcorrenciaIA(4, 1, 4);

        limitador.adquirir(UUID.randomUUID(), RecursoIA.TRANSCRICAO);

        assertThatThrownBy(() -> limitador.adquirir(UUID.randomUUID(), RecursoIA.TRANSCRICAO))
                .isInstanceOf(IAOcupadaException.class);
        assertThat(limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO)).isNotNull();
    }

    @Test
    void valorZeroDerrubaASubidaComONomeDaVariavel() {
        assertThatThrownBy(() -> new ConcorrenciaIAConfig().limitadorConcorrenciaIA(4, 2, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("IA_CONCORRENCIA_JULIA deve ser maior que zero");
    }

    @Test
    void valorNegativoDerrubaASubidaComONomeDaVariavel() {
        assertThatThrownBy(() -> new ConcorrenciaIAConfig().limitadorConcorrenciaIA(-1, 2, 4))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("IA_CONCORRENCIA_CORRECAO deve ser maior que zero");
    }
}
