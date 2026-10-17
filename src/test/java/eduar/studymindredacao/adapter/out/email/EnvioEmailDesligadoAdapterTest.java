package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

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
}
