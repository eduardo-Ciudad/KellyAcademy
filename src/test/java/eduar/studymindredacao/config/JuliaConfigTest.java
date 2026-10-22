package eduar.studymindredacao.config;

import eduar.studymindredacao.application.usecase.PoliticaJulia;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JuliaConfigTest {

    @Test
    void criaAPoliticaComOsValoresDasProperties() {
        PoliticaJulia politica = new JuliaConfig().politicaJulia(10, 500, 6, 24, 20, 100);

        assertThat(politica.limites().limitePorConversa()).isEqualTo(10);
        assertThat(politica.limites().tamanhoMaxPergunta()).isEqualTo(500);
        assertThat(politica.limites().historicoMaximo()).isEqualTo(6);
        assertThat(politica.limites().inatividadeMaxima()).isEqualTo(Duration.ofHours(24));
        assertThat(politica.limiteDiario()).isEqualTo(20);
        assertThat(politica.tetoGlobal()).isEqualTo(100);
    }

    @Test
    void configuracaoIncoerenteDerrubaASubida() {
        assertThatThrownBy(() -> new JuliaConfig().politicaJulia(10, 500, 5, 24, 20, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
