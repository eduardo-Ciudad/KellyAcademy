package eduar.studymindredacao.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenEmitidoTest {
    private static final String HASH = "0123456789abcdef".repeat(4);

    @Test
    void guardaValorEHash() {
        var token = new TokenEmitido("valor-em-claro", HASH);

        assertThat(token.valorEmClaro()).isEqualTo("valor-em-claro");
        assertThat(token.hash()).isEqualTo(HASH);
    }

    @Test
    void toStringNaoMostraValorNemHash() {
        String texto = new TokenEmitido("valor-em-claro", HASH).toString();

        assertThat(texto).doesNotContain("valor-em-claro").doesNotContain(HASH);
    }

    @Test
    void recusaValorVazio() {
        assertThatThrownBy(() -> new TokenEmitido(" ", HASH)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenEmitido(null, HASH)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaHashInvalido() {
        assertThatThrownBy(() -> new TokenEmitido("valor", "nao-e-hash"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash");
    }
}
