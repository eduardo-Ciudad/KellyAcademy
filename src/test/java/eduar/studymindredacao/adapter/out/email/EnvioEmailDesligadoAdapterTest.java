package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemRedefinicaoSenha;
import eduar.studymindredacao.domain.model.MensagemSenhaAlterada;
import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvioEmailDesligadoAdapterTest {

    @Test
    void naoEnviaENaoRegistraLinkNemEmailCompleto() {
        var mensagem = new MensagemVerificacaoEmail("ana@exemplo.com", "Ana",
                "https://kellyacademy.com.br/verificar-email#token=SegredoXYZ");

        try (var logs = new LogsCapturados(EnvioEmailDesligadoAdapter.class)) {
            assertThatCode(() -> new EnvioEmailDesligadoAdapter().enviarVerificacao(mensagem)).doesNotThrowAnyException();

            assertThat(logs.tudo())
                    .contains("EMAIL_HABILITADO=false")
                    .contains("***@exemplo.com")
                    .doesNotContain("SegredoXYZ")
                    .doesNotContain("ana@exemplo.com");
        }
    }

    @Test
    void redefinicaoNaoEnviaENaoRegistraOLink() {
        var mensagem = new MensagemRedefinicaoSenha("ana@exemplo.com", "Ana",
                "https://kellyacademy.com.br/redefinir-senha#token=SegredoXYZ");

        try (var logs = new LogsCapturados(EnvioEmailDesligadoAdapter.class)) {
            assertThatCode(() -> new EnvioEmailDesligadoAdapter().enviarRedefinicaoSenha(mensagem)).doesNotThrowAnyException();

            assertThat(logs.tudo())
                    .contains("redefinição de senha")
                    .contains("***@exemplo.com")
                    .doesNotContain("SegredoXYZ")
                    .doesNotContain("ana@exemplo.com");
        }
    }

    @Test
    void avisoNaoEnviaENaoRegistraOEmailCompleto() {
        var mensagem = new MensagemSenhaAlterada("ana@exemplo.com", "Ana", OffsetDateTime.now(),
                "https://kellyacademy.com.br/esqueci-senha");

        try (var logs = new LogsCapturados(EnvioEmailDesligadoAdapter.class)) {
            assertThatCode(() -> new EnvioEmailDesligadoAdapter().enviarAvisoSenhaAlterada(mensagem)).doesNotThrowAnyException();

            assertThat(logs.tudo()).contains("aviso de senha alterada").doesNotContain("ana@exemplo.com");
        }
    }

    @Test
    void recusaMensagemNula() {
        var adapter = new EnvioEmailDesligadoAdapter();

        assertThatThrownBy(() -> adapter.enviarVerificacao(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.enviarRedefinicaoSenha(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> adapter.enviarAvisoSenhaAlterada(null)).isInstanceOf(NullPointerException.class);
    }
}
