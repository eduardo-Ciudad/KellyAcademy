package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModeloEmailVerificacaoTest {
    private static final String LINK = "https://kellyacademy.com.br/verificar-email#token=AbC_123-xyz";
    private final ModeloEmailVerificacao modelo = new ModeloEmailVerificacao(Duration.ofHours(24));

    private EmailMontado montar(String nome, String link) {
        return modelo.montar(new MensagemVerificacaoEmail("ana@exemplo.com", nome, link));
    }

    @Test
    void montaAssuntoFixoDestinatarioETextoComOLink() {
        var email = montar("Ana", LINK);

        assertThat(email.destinatario()).isEqualTo("ana@exemplo.com");
        assertThat(email.assunto()).isEqualTo(ModeloEmailVerificacao.ASSUNTO);
        assertThat(email.texto()).contains("Olá, Ana!").contains(LINK).contains("24 horas");
        assertThat(email.html()).contains("href=\"" + LINK + "\"").contains("Olá, Ana!").contains("24 horas");
    }

    @Test
    void escapaHtmlNoNome() {
        var email = montar("<script>alert('x')</script> \"Ana\"", LINK);

        assertThat(email.html())
                .doesNotContain("<script>")
                .doesNotContain("alert('x')")
                .contains("&lt;script&gt;")
                .contains("&quot;Ana&quot;");
    }

    @Test
    void acentosNaoViramEntidade() {
        assertThat(montar("José Conceição", LINK).html()).contains("José Conceição");
    }

    @Test
    void removeQuebrasDeLinhaEControlesDoNome() {
        var email = montar("Ana\r\nBcc: atacante@exemplo.com X\u0000", LINK);

        assertThat(email.texto()).startsWith("Olá, Ana Bcc: atacante@exemplo.com X!");
        assertThat(email.texto().split("\n")[0]).doesNotContain("\r");
        assertThat(ModeloEmailVerificacao.limparNome("a\tb\nc")).isEqualTo("a b c");
    }

    @Test
    void cortaNomeMuitoLongo() {
        String longo = "A".repeat(150);

        assertThat(ModeloEmailVerificacao.limparNome(longo)).hasSize(ModeloEmailVerificacao.TAMANHO_MAXIMO_NOME);
    }

    @Test
    void nomeSoComControlesViraSaudacaoGenerica() {
        assertThat(montar("\u0000\u0001", LINK).texto()).startsWith("Olá!\n");
    }

    @Test
    void aceitaHttpSoEmLocalhost() {
        assertThat(montar("Ana", "http://localhost:5173/verificar-email#token=x").texto()).contains("localhost:5173");
        assertThat(montar("Ana", "http://127.0.0.1:5173/verificar-email#token=x").texto()).contains("127.0.0.1");
    }

    @Test
    void recusaLinkSemHttpsOuComEsquemaPerigoso() {
        for (String link : new String[]{
                "http://kellyacademy.com.br/verificar-email#token=x",
                "javascript:alert(1)",
                "http://localhost.evil.com/x",
                "ftp://kellyacademy.com.br"}) {
            assertThatThrownBy(() -> montar("Ana", link)).as(link).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void recusaLinkComEspacoOuQuebra() {
        assertThatThrownBy(() -> montar("Ana", "https://kellyacademy.com.br/x\r\nBcc: y"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> montar("Ana", "https://kellyacademy.com.br/x y"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descreveValidadeEmHorasOuMinutos() {
        var umaHora = new ModeloEmailVerificacao(Duration.ofHours(1));
        var meiaHora = new ModeloEmailVerificacao(Duration.ofMinutes(30));
        var mensagem = new MensagemVerificacaoEmail("ana@exemplo.com", "Ana", LINK);

        assertThat(umaHora.montar(mensagem).texto()).contains("1 hora ");
        assertThat(meiaHora.montar(mensagem).texto()).contains("30 minutos");
    }

    @Test
    void recusaValidadeInvalida() {
        assertThatThrownBy(() -> new ModeloEmailVerificacao(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModeloEmailVerificacao(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void toStringDoEmailMontadoNaoMostraOLink() {
        assertThat(montar("Ana", LINK).toString()).doesNotContain("AbC_123-xyz").doesNotContain("ana@exemplo.com");
    }
}
