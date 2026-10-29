package eduar.studymindredacao.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessaoPortalTest {
    private static final String ID = "bps_1Abc23";
    private static final String URL = "https://billing.stripe.com/p/session/test_abc";

    @Test
    void aceitaIdDoPortalComUrlHttps() {
        var sessao = new SessaoPortal(ID, URL);

        assertThat(sessao.id()).isEqualTo(ID);
        assertThat(sessao.url()).isEqualTo(URL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejeitaIdAusente(String id) {
        assertThatThrownBy(() -> new SessaoPortal(id, URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    void rejeitaIdQueNaoEDoPortal() {
        // Um id de checkout (cs_) aqui indicaria troca de chamada no adapter
        assertThatThrownBy(() -> new SessaoPortal("cs_123", URL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bps_");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void rejeitaUrlAusente(String url) {
        assertThatThrownBy(() -> new SessaoPortal(ID, url))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("url");
    }

    @Test
    void rejeitaUrlSemHttps() {
        assertThatThrownBy(() -> new SessaoPortal(ID, "http://billing.stripe.com/p/session/x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("https");
    }

    @Test
    void rejeitaUrlLongaDemais() {
        String longa = "https://" + "a".repeat(2048);

        assertThatThrownBy(() -> new SessaoPortal(ID, longa))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
