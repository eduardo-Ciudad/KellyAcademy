package eduar.studymindredacao.adapter.out.security;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecureRandomTokenAdapterTest {
    private final SecureRandomTokenAdapter adapter = new SecureRandomTokenAdapter();

    @Test
    void valorTem256BitsEmBase64UrlSemPadding() {
        String valor = adapter.emitir().valorEmClaro();

        // 32 bytes em Base64 sem "=" = 43 caracteres; só A-Z a-z 0-9 - _ (seguro em URL, sem escapar)
        assertThat(valor).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
    }

    @Test
    void tokensNaoSeRepetem() {
        Set<String> valores = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            valores.add(adapter.emitir().valorEmClaro());
        }

        assertThat(valores).hasSize(1_000);
    }

    @Test
    void hashEmitidoEhOHashDoValor() {
        var token = adapter.emitir();

        assertThat(token.hash()).isEqualTo(adapter.hash(token.valorEmClaro()));
        assertThat(token.hash()).isNotEqualTo(token.valorEmClaro());
    }

    @Test
    void hashEhSha256EmHexMinusculoEDeterministico() {
        String hash = adapter.hash("token-qualquer");

        assertThat(hash).matches("^[0-9a-f]{64}$");
        assertThat(adapter.hash("token-qualquer")).isEqualTo(hash);
        assertThat(adapter.hash("token-qualquex")).isNotEqualTo(hash);
    }

    @Test
    void hashBateComVetorConhecidoDoSha256() {
        // SHA-256("abc"), vetor de teste do FIPS 180-2
        assertThat(adapter.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void usaOGeradorAleatorioRecebido() {
        var fixo = new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                java.util.Arrays.fill(bytes, (byte) 0);
            }
        };

        var token = new SecureRandomTokenAdapter(fixo).emitir();

        assertThat(token.valorEmClaro()).isEqualTo("A".repeat(43));
    }

    @Test
    void hashExigeValor() {
        assertThatThrownBy(() -> adapter.hash(null)).isInstanceOf(NullPointerException.class);
    }
}
