package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemSenhaAlterada;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModeloEmailSenhaAlteradaTest {
    private static final String LINK = "https://kellyacademy.com.br/esqueci-senha";
    // 17:05 em UTC = 14:05 em Brasília
    private static final OffsetDateTime QUANDO_UTC = OffsetDateTime.of(2026, 10, 18, 17, 5, 0, 0, ZoneOffset.UTC);
    private final ModeloEmailSenhaAlterada modelo = new ModeloEmailSenhaAlterada();

    private EmailMontado montar(String nome, OffsetDateTime quando, String link) {
        return modelo.montar(new MensagemSenhaAlterada("ana@exemplo.com", nome, quando, link));
    }

    @Test
    void montaAssuntoEDestinatario() {
        var email = montar("Ana", QUANDO_UTC, LINK);

        assertThat(email.destinatario()).isEqualTo("ana@exemplo.com");
        assertThat(email.assunto()).isEqualTo("Sua senha da Kelly Academy foi alterada");
        assertThat(email.texto()).startsWith("Olá, Ana!");
    }

    @Test
    void mostraADataNoHorarioDeBrasilia() {
        var email = montar("Ana", QUANDO_UTC, LINK);

        assertThat(email.texto()).contains("18/10/2026 às 14:05 (horário de Brasília)");
        assertThat(email.html()).contains("18/10/2026 às 14:05");
    }

    @Test
    void mesmoInstanteEmOutroFusoDaAMesmaHora() {
        var emLisboa = QUANDO_UTC.withOffsetSameInstant(ZoneOffset.ofHours(1));

        assertThat(montar("Ana", emLisboa, LINK).texto()).contains("18/10/2026 às 14:05");
    }

    @Test
    void levaParaOEsqueciMinhaSenhaSemNenhumToken() {
        var email = montar("Ana", QUANDO_UTC, LINK);

        assertThat(email.texto()).contains(LINK).contains("Não foi você?").doesNotContain("#token");
        assertThat(email.html()).contains("href=\"" + LINK + "\"").doesNotContain("#token");
    }

    @Test
    void avisaQueOsAparelhosForamDesconectados() {
        assertThat(montar("Ana", QUANDO_UTC, LINK).texto()).contains("foram desconectados");
    }

    @Test
    void escapaHtmlNoNome() {
        assertThat(montar("<img src=x onerror=alert(1)>", QUANDO_UTC, LINK).html())
                .doesNotContain("<img")
                .contains("&lt;img");
    }

    @Test
    void recusaLinkSemHttps() {
        assertThatThrownBy(() -> montar("Ana", QUANDO_UTC, "http://kellyacademy.com.br/esqueci-senha"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
