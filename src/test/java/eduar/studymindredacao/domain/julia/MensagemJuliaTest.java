package eduar.studymindredacao.domain.julia;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensagemJuliaTest {

    private static final Instant AGORA = Instant.parse("2026-10-21T20:00:00Z");
    private static final int TAMANHO_MAX = 500;

    @Test
    void deveCriarPerguntaRemovendoEspacosDasPontas() {
        MensagemJulia mensagem = MensagemJulia.doUsuario("  Por que perdi pontos na C3?  ", TAMANHO_MAX, AGORA);

        assertThat(mensagem.conteudo()).isEqualTo("Por que perdi pontos na C3?");
        assertThat(mensagem.papel()).isEqualTo(PapelMensagem.USUARIO);
        assertThat(mensagem.ehDoUsuario()).isTrue();
        assertThat(mensagem.id()).isNull();
        assertThat(mensagem.tokensEntrada()).isNull();
        assertThat(mensagem.tokensSaida()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\n\t"})
    void deveRejeitarPerguntaVazia(String texto) {
        assertThatThrownBy(() -> MensagemJulia.doUsuario(texto, TAMANHO_MAX, AGORA))
                .isInstanceOf(PerguntaInvalidaException.class);
    }

    @Test
    void deveAceitarPerguntaNoLimiteExato() {
        MensagemJulia mensagem = MensagemJulia.doUsuario("a".repeat(500), TAMANHO_MAX, AGORA);

        assertThat(mensagem.conteudo()).hasSize(500);
    }

    @Test
    void deveRejeitarPerguntaAcimaDoLimite() {
        assertThatThrownBy(() -> MensagemJulia.doUsuario("a".repeat(501), TAMANHO_MAX, AGORA))
                .isInstanceOf(PerguntaInvalidaException.class)
                .hasMessageContaining("500");
    }

    @Test
    void deveMedirTamanhoDepoisDeRemoverEspacos() {
        String texto = "   " + "a".repeat(500) + "   ";

        MensagemJulia mensagem = MensagemJulia.doUsuario(texto, TAMANHO_MAX, AGORA);

        assertThat(mensagem.conteudo()).hasSize(500);
    }

    @Test
    void deveCriarMensagemDaJuliaComTokens() {
        MensagemJulia mensagem = MensagemJulia.daJulia(new RespostaJulia("Na C3...", 900, 120), AGORA);

        assertThat(mensagem.papel()).isEqualTo(PapelMensagem.JULIA);
        assertThat(mensagem.ehDoUsuario()).isFalse();
        assertThat(mensagem.tokensEntrada()).isEqualTo(900);
        assertThat(mensagem.tokensSaida()).isEqualTo(120);
    }
}