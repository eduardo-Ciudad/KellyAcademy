package  eduar.studymindredacao.domain.julia;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RespostaJuliaTest {

    @Test
    void deveRemoverEspacosDasPontas() {
        RespostaJulia resposta = new RespostaJulia("  Na C3 você perdeu pontos porque...  ", 900, 120);

        assertThat(resposta.texto()).isEqualTo("Na C3 você perdeu pontos porque...");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void deveRejeitarTextoVazio(String texto) {
        assertThatThrownBy(() -> new RespostaJulia(texto, 900, 120))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarTokensNegativos() {
        assertThatThrownBy(() -> new RespostaJulia("ok", -1, 120))
                .isInstanceOf(IllegalArgumentException.class);
    }
}