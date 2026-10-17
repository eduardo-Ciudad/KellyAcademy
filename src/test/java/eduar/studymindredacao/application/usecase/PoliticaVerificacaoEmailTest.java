package eduar.studymindredacao.application.usecase;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaVerificacaoEmailTest {

    @Test
    void desligadaNaoExigeUrl() {
        var politica = PoliticaVerificacaoEmail.desligada();

        assertThat(politica.obrigatoria()).isFalse();
        assertThat(politica.urlBaseFrontend()).isEmpty();
    }

    @Test
    void linkLevaOTokenNoFragmento() {
        var politica = new PoliticaVerificacaoEmail(true, Duration.ofHours(24), " https://kellyacademy.com.br ");

        assertThat(politica.linkPara("abc_123"))
                .isEqualTo("https://kellyacademy.com.br/verificar-email#token=abc_123");
    }

    @Test
    void ligadaExigeUrlDoFront() {
        assertThatThrownBy(() -> new PoliticaVerificacaoEmail(true, Duration.ofHours(24), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APP_FRONTEND_URL");
        assertThatThrownBy(() -> new PoliticaVerificacaoEmail(true, Duration.ofHours(24), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaUrlComBarraNoFinal() {
        assertThatThrownBy(() -> new PoliticaVerificacaoEmail(true, Duration.ofHours(24), "https://kellyacademy.com.br/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("barra");
    }

    @Test
    void recusaValidadeZeradaNegativaOuNula() {
        assertThatThrownBy(() -> new PoliticaVerificacaoEmail(false, Duration.ZERO, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaVerificacaoEmail(false, Duration.ofHours(-1), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaVerificacaoEmail(false, null, ""))
                .isInstanceOf(NullPointerException.class);
    }
}
