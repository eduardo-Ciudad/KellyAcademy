package eduar.studymindredacao.domain.julia;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitesJuliaTest {

    private static final Duration DIA = Duration.ofHours(24);

    @Test
    void deveAceitarLimitesValidos() {
        assertThatCode(() -> new LimitesJulia(10, 500, 6, DIA)).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarHistoricoImpar() {
        assertThatThrownBy(() -> new LimitesJulia(10, 500, 5, DIA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarLimitePorConversaZero() {
        assertThatThrownBy(() -> new LimitesJulia(0, 500, 6, DIA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarInatividadeZero() {
        assertThatThrownBy(() -> new LimitesJulia(10, 500, 6, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
