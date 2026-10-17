package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.TipoTokenUsuario;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenUsuarioTest {
    private static final String HASH = "a".repeat(64);
    private static final UUID USUARIO = UUID.randomUUID();
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 16, 21, 0, 0, 0, ZoneOffset.ofHours(-3));

    private static TokenUsuario novo() {
        return TokenUsuario.novo(USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, HASH, AGORA, Duration.ofHours(24));
    }

    @Test
    void novoNasceSemUsoEExpiraDepoisDaValidade() {
        var token = novo();

        assertThat(token.id()).isNull();
        assertThat(token.usuarioId()).isEqualTo(USUARIO);
        assertThat(token.tipo()).isEqualTo(TipoTokenUsuario.VERIFICACAO_EMAIL);
        assertThat(token.criadoEm()).isEqualTo(AGORA);
        assertThat(token.expiraEm()).isEqualTo(AGORA.plusHours(24));
        assertThat(token.usadoEm()).isNull();
    }

    @Test
    void valeAntesDeExpirar() {
        assertThat(novo().estaValido(AGORA)).isTrue();
        assertThat(novo().estaValido(AGORA.plusHours(24).minusNanos(1))).isTrue();
    }

    @Test
    void noInstanteExatoDaExpiracaoJaNaoVale() {
        assertThat(novo().estaValido(AGORA.plusHours(24))).isFalse();
    }

    @Test
    void depoisDeExpirarNaoVale() {
        assertThat(novo().estaValido(AGORA.plusDays(2))).isFalse();
    }

    @Test
    void tokenJaUsadoNaoValeMesmoDentroDoPrazo() {
        var usado = new TokenUsuario(UUID.randomUUID(), USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, HASH,
                AGORA, AGORA.plusHours(24), AGORA.plusMinutes(5));

        assertThat(usado.estaValido(AGORA.plusMinutes(10))).isFalse();
    }

    @Test
    void recusaValidadeZeradaOuNegativa() {
        assertThatThrownBy(() -> TokenUsuario.novo(USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, HASH, AGORA, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TokenUsuario.novo(USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, HASH, AGORA, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaExpiracaoAntesOuIgualACriacao() {
        assertThatThrownBy(() -> new TokenUsuario(null, USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, HASH, AGORA, AGORA, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiraEm");
    }

    @Test
    void recusaHashForaDoFormatoSha256() {
        for (String invalido : new String[]{null, "", "abc", "A".repeat(64), "g".repeat(64), "a".repeat(63), "a".repeat(65)}) {
            assertThatThrownBy(() -> TokenUsuario.novo(USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, invalido, AGORA, Duration.ofHours(1)))
                    .as("hash %s", invalido)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("tokenHash");
        }
    }

    @Test
    void mensagemDeHashInvalidoNaoRepeteOValor() {
        String parecidoComToken = "segredo-que-nao-pode-ir-para-o-log";

        assertThatThrownBy(() -> TokenUsuario.novo(USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, parecidoComToken, AGORA, Duration.ofHours(1)))
                .hasMessageNotContaining(parecidoComToken);
    }

    @Test
    void exigeCamposObrigatorios() {
        assertThatThrownBy(() -> TokenUsuario.novo(null, TipoTokenUsuario.VERIFICACAO_EMAIL, HASH, AGORA, Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("usuarioId");
        assertThatThrownBy(() -> TokenUsuario.novo(USUARIO, null, HASH, AGORA, Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("tipo");
        assertThatThrownBy(() -> TokenUsuario.novo(USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, HASH, null, Duration.ofHours(1)))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("agora");
        assertThatThrownBy(() -> TokenUsuario.novo(USUARIO, TipoTokenUsuario.VERIFICACAO_EMAIL, HASH, AGORA, null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("validade");
        assertThatThrownBy(() -> novo().estaValido(null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("agora");
    }

    @Test
    void toStringNaoMostraOHash() {
        assertThat(novo().toString()).doesNotContain(HASH).contains("tokenHash=***");
    }
}
