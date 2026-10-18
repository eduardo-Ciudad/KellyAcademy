package eduar.studymindredacao.application.usecase;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaRedefinicaoSenhaTest {
    private static final Duration MEIA_HORA = Duration.ofMinutes(30);

    @Test
    void montaOLinkComOTokenNoFragmentoEOLinkDoEsqueciSenha() {
        var politica = new PoliticaRedefinicaoSenha(true, MEIA_HORA, " https://kellyacademy.com.br ");

        assertThat(politica.linkPara("abc_123")).isEqualTo("https://kellyacademy.com.br/redefinir-senha#token=abc_123");
        assertThat(politica.linkEsqueciSenha()).isEqualTo("https://kellyacademy.com.br/esqueci-senha");
    }

    @Test
    void habilitadaExigeUrlDoFront() {
        assertThatThrownBy(() -> new PoliticaRedefinicaoSenha(true, MEIA_HORA, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("APP_FRONTEND_URL");
        assertThatThrownBy(() -> new PoliticaRedefinicaoSenha(true, MEIA_HORA, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void desligadaNaoExigeUrl() {
        var politica = PoliticaRedefinicaoSenha.desligada();

        assertThat(politica.habilitada()).isFalse();
        assertThat(politica.urlBaseFrontend()).isEmpty();
    }

    @Test
    void recusaUrlComBarraNoFinal() {
        assertThatThrownBy(() -> new PoliticaRedefinicaoSenha(true, MEIA_HORA, "https://kellyacademy.com.br/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("barra");
    }

    @Test
    void recusaValidadeZeradaNegativaOuNula() {
        assertThatThrownBy(() -> new PoliticaRedefinicaoSenha(false, Duration.ZERO, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRedefinicaoSenha(false, Duration.ofMinutes(-1), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaRedefinicaoSenha(false, null, ""))
                .isInstanceOf(NullPointerException.class);
    }
}
