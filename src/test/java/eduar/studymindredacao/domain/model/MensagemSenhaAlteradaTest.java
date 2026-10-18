package eduar.studymindredacao.domain.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensagemSenhaAlteradaTest {
    private static final OffsetDateTime QUANDO = OffsetDateTime.of(2026, 10, 18, 14, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final String LINK = "https://kellyacademy.com.br/esqueci-senha";

    @Test
    void guardaOsDados() {
        var mensagem = new MensagemSenhaAlterada("aluno@exemplo.com", "Ana", QUANDO, LINK);

        assertThat(mensagem.destinatario()).isEqualTo("aluno@exemplo.com");
        assertThat(mensagem.nome()).isEqualTo("Ana");
        assertThat(mensagem.alteradaEm()).isEqualTo(QUANDO);
        assertThat(mensagem.linkEsqueciSenha()).isEqualTo(LINK);
    }

    @Test
    void exigeTodosOsCampos() {
        assertThatThrownBy(() -> new MensagemSenhaAlterada(null, "Ana", QUANDO, LINK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("destinatario");
        assertThatThrownBy(() -> new MensagemSenhaAlterada("aluno@exemplo.com", " ", QUANDO, LINK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nome");
        assertThatThrownBy(() -> new MensagemSenhaAlterada("aluno@exemplo.com", "Ana", null, LINK))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("alteradaEm");
        assertThatThrownBy(() -> new MensagemSenhaAlterada("aluno@exemplo.com", "Ana", QUANDO, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("linkEsqueciSenha");
    }
}
