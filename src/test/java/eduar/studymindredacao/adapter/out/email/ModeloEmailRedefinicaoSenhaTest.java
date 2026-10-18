package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemRedefinicaoSenha;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModeloEmailRedefinicaoSenhaTest {
    private static final String LINK = "https://kellyacademy.com.br/redefinir-senha#token=AbC_123-xyz";
    private final ModeloEmailRedefinicaoSenha modelo = new ModeloEmailRedefinicaoSenha(Duration.ofMinutes(30));

    private EmailMontado montar(String nome, String link) {
        return modelo.montar(new MensagemRedefinicaoSenha("ana@exemplo.com", nome, link));
    }

    @Test
    void montaAssuntoDestinatarioEOLinkNoTextoENoHtml() {
        var email = montar("Ana", LINK);

        assertThat(email.destinatario()).isEqualTo("ana@exemplo.com");
        assertThat(email.assunto()).isEqualTo("Redefinir sua senha da Kelly Academy");
        assertThat(email.texto()).startsWith("Olá, Ana!").contains(LINK);
        assertThat(email.html()).contains("href=\"" + LINK + "\"").contains("Redefinir senha");
    }

    @Test
    void avisaAValidadeDe30MinutosEQueQuemNaoPediuPodeIgnorar() {
        var email = montar("Ana", LINK);

        assertThat(email.texto()).contains("30 minutos").contains("sua senha continua a mesma");
        assertThat(email.html()).contains("30 minutos").contains("sua senha continua a mesma");
    }

    @Test
    void escapaHtmlNoNome() {
        assertThat(montar("<script>alert('x')</script>", LINK).html())
                .doesNotContain("<script>")
                .contains("&lt;script&gt;");
    }

    @Test
    void removeQuebrasDeLinhaDoNome() {
        var email = montar("Ana\r\nBcc: atacante@exemplo.com", LINK);

        assertThat(email.texto().split("\n")[0]).isEqualTo("Olá, Ana Bcc: atacante@exemplo.com!");
    }

    @Test
    void recusaLinkSemHttps() {
        assertThatThrownBy(() -> montar("Ana", "http://kellyacademy.com.br/redefinir-senha#token=x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> montar("Ana", "javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringDoEmailMontadoNaoMostraOLink() {
        assertThat(montar("Ana", LINK).toString()).doesNotContain("AbC_123-xyz").doesNotContain("ana@exemplo.com");
    }

    @Test
    void recusaValidadeInvalida() {
        assertThatThrownBy(() -> new ModeloEmailRedefinicaoSenha(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModeloEmailRedefinicaoSenha(null)).isInstanceOf(NullPointerException.class);
    }
}
