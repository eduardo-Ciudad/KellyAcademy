package eduar.studymindredacao.adapter.out.email;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextoEmailSeguroTest {

    @Test
    void descreveValidadeEmHorasOuMinutos() {
        assertThat(TextoEmailSeguro.descreverValidade(Duration.ofMinutes(30))).isEqualTo("30 minutos");
        assertThat(TextoEmailSeguro.descreverValidade(Duration.ofHours(1))).isEqualTo("1 hora");
        assertThat(TextoEmailSeguro.descreverValidade(Duration.ofHours(24))).isEqualTo("24 horas");
        assertThat(TextoEmailSeguro.descreverValidade(Duration.ofMinutes(1))).isEqualTo("1 minuto");
        assertThat(TextoEmailSeguro.descreverValidade(Duration.ofMinutes(90))).isEqualTo("90 minutos");
    }

    @Test
    void recusaValidadeNulaZeradaOuNegativa() {
        assertThatThrownBy(() -> TextoEmailSeguro.exigirValidade(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TextoEmailSeguro.exigirValidade(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextoEmailSeguro.exigirValidade(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validarLinkAceitaHttpsEHttpSoEmLocalhost() {
        assertThat(TextoEmailSeguro.validarLink(" https://kellyacademy.com.br/x ")).isEqualTo("https://kellyacademy.com.br/x");
        assertThat(TextoEmailSeguro.validarLink("http://localhost:5173/x")).isEqualTo("http://localhost:5173/x");
    }

    @Test
    void validarLinkRecusaEsquemasPerigososEspacoEQuebra() {
        for (String link : new String[]{
                "javascript:alert(1)",
                "http://kellyacademy.com.br/x",
                "http://localhost.evil.com/x",
                "https://kellyacademy.com.br/x y",
                "https://kellyacademy.com.br/x\r\nBcc: y",
                null}) {
            assertThatThrownBy(() -> TextoEmailSeguro.validarLink(link)).as(String.valueOf(link))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void limparNomeRemoveQuebrasControlesESeparadoresUnicode() {
        assertThat(TextoEmailSeguro.limparNome("Ana\r\nBcc: x y\u0000")).isEqualTo("Ana Bcc: x y");
        assertThat(TextoEmailSeguro.limparNome(null)).isEmpty();
    }

    @Test
    void saudacaoComESemNome() {
        assertThat(TextoEmailSeguro.saudacao("  Ana  ")).isEqualTo("Olá, Ana!");
        assertThat(TextoEmailSeguro.saudacao("\u0000")).isEqualTo("Olá!");
    }

    @Test
    void blocosDeHtmlEscapamOTexto() {
        assertThat(TextoEmailSeguro.htmlParagrafo("<b>oi</b> & tchau")).contains("&lt;b&gt;oi&lt;/b&gt; &amp; tchau");
        assertThat(TextoEmailSeguro.htmlBotao("https://x.com/a?b=1&c=2", "<Ir>"))
                .contains("href=\"https://x.com/a?b=1&amp;c=2\"")
                .contains("&lt;Ir&gt;");
    }

    @Test
    void paginaHtmlTemUmaTabelaComLarguraTotal() {
        assertThat(TextoEmailSeguro.htmlPagina("<p>a</p>", "<p>b</p>"))
                .startsWith("<!doctype html>")
                .contains("width=\"100%\"")
                .contains("<p>a</p>\n<p>b</p>")
                .endsWith("</html>\n");
    }
}
