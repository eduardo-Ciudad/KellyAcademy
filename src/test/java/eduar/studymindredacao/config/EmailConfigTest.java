package eduar.studymindredacao.config;

import eduar.studymindredacao.adapter.out.email.EnvioEmailDesligadoAdapter;
import eduar.studymindredacao.adapter.out.email.SmtpEnvioEmailAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailConfigTest {

    /** Falha se for consultado: com o e-mail desligado o JavaMailSender nem deve ser pedido. */
    private static Supplier<JavaMailSender> providerQueNaoPodeSerUsado() {
        return () -> {
            throw new AssertionError("JavaMailSender não deveria ser pedido com o e-mail desligado");
        };
    }

    private static Supplier<JavaMailSender> provider(JavaMailSender sender) {
        return () -> sender;
    }

    @Test
    void emailDesligadoUsaOAdapterQueNaoEnvia() {
        var porta = EmailConfig.criar(false, Duration.ofHours(24), 100, "", "Kelly Academy", providerQueNaoPodeSerUsado());

        assertThat(porta).isInstanceOf(EnvioEmailDesligadoAdapter.class);
    }

    @Test
    void emailLigadoUsaSmtp() throws Exception {
        var porta = EmailConfig.criar(true, Duration.ofHours(24), 100, "kellyacademy@gmail.com", "Kelly Academy",
                provider(new JavaMailSenderImpl()));

        assertThat(porta).isInstanceOf(SmtpEnvioEmailAdapter.class);
        ((SmtpEnvioEmailAdapter) porta).close();
    }

    @Test
    void leValidadeEmIso8601() {
        assertThat(EmailConfig.validadeVerificacao("PT24H")).isEqualTo(Duration.ofHours(24));
        assertThat(EmailConfig.validadeVerificacao(" PT30M ")).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void recusaValidadeInvalidaZeradaOuNegativa() {
        assertThatThrownBy(() -> EmailConfig.validadeVerificacao("24h"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ISO-8601");
        assertThatThrownBy(() -> EmailConfig.validadeVerificacao("PT0S"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("positiva");
        assertThatThrownBy(() -> EmailConfig.validadeVerificacao("-PT1H"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EmailConfig.validadeVerificacao(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
