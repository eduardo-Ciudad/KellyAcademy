package eduar.studymindredacao.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracaoInicialTest {

    private static ConfiguracaoInicial valida() {
        return new ConfiguracaoInicial(3, 30, 3, 30, "gemini-3.5-flash-lite", "gemini-3.5-flash-lite",
                false, "smtp.gmail.com", "587", "", "", "");
    }

    private static ConfiguracaoInicial comEmail(String porta, String usuario, String senha, String frontendUrl) {
        return new ConfiguracaoInicial(3, 30, 3, 30, "gemini-3.5-flash-lite", "gemini-3.5-flash-lite",
                true, "smtp.gmail.com", porta, usuario, senha, frontendUrl);
    }

    @Test
    void configuracaoPadraoComEmailDesligadoEhValida() {
        assertThat(valida().validar()).isEmpty();
    }

    @Test
    void emailDesligadoNaoExigeCredenciais() {
        var config = new ConfiguracaoInicial(3, 30, 3, 30, "m", "m", false, "", "", "", "", "");

        assertThat(config.validar()).isEmpty();
    }

    @Test
    void recusaLimitesZeradosOuNegativos() {
        var config = new ConfiguracaoInicial(0, 30, -1, 30, "m", "m", false, "", "", "", "", "");

        assertThat(config.validar())
                .anyMatch(e -> e.startsWith("LIMITE_CORRECOES_DIARIAS"))
                .anyMatch(e -> e.startsWith("LIMITE_TRANSCRICOES_DIARIAS"));
    }

    @Test
    void recusaLimiteDoAlunoMaiorQueOTetoGlobal() {
        var config = new ConfiguracaoInicial(40, 30, 3, 2, "m", "m", false, "", "", "", "", "");

        assertThat(config.validar())
                .contains("LIMITE_CORRECOES_DIARIAS não pode ser maior que LIMITE_CORRECOES_GLOBAL")
                .contains("LIMITE_TRANSCRICOES_DIARIAS não pode ser maior que LIMITE_TRANSCRICOES_GLOBAL");
    }

    @Test
    void recusaModeloDaTranscricaoVazio() {
        var config = new ConfiguracaoInicial(3, 30, 3, 30, "m", " ", false, "", "", "", "", "");

        assertThat(config.validar()).anyMatch(e -> e.startsWith("GEMINI_MODELO_TRANSCRICAO"));
    }

    @Test
    void emailLigadoComTudoPreenchidoEhValido() {
        assertThat(comEmail("587", "kelly@gmail.com", "abcd efgh ijkl mnop", "https://app.kellyacademy.com.br")
                .validar()).isEmpty();
    }

    @Test
    void emailLigadoAceitaHttpEmLocalhost() {
        assertThat(comEmail("587", "u", "s", "http://localhost:5173").validar()).isEmpty();
        assertThat(comEmail("587", "u", "s", "http://127.0.0.1:5173").validar()).isEmpty();
    }

    @Test
    void emailLigadoExigeUsuarioSenhaEUrl() {
        assertThat(comEmail("587", "", " ", "").validar())
                .contains("MAIL_USERNAME é obrigatória com EMAIL_HABILITADO=true")
                .contains("MAIL_PASSWORD é obrigatória com EMAIL_HABILITADO=true")
                .contains("APP_FRONTEND_URL é obrigatória com EMAIL_HABILITADO=true");
    }

    @Test
    void emailLigadoRecusaPortaInvalida() {
        assertThat(comEmail("abc", "u", "s", "https://a.com").validar()).contains("MAIL_PORT deve ser um número");
        assertThat(comEmail("70000", "u", "s", "https://a.com").validar())
                .contains("MAIL_PORT deve estar entre 1 e 65535");
    }

    @Test
    void emailLigadoRecusaHttpForaDeLocalhostEBarraNoFinal() {
        assertThat(comEmail("587", "u", "s", "http://app.kellyacademy.com.br").validar())
                .anyMatch(e -> e.contains("deve usar https"));
        assertThat(comEmail("587", "u", "s", "https://app.kellyacademy.com.br/").validar())
                .contains("APP_FRONTEND_URL não deve terminar com barra");
        assertThat(comEmail("587", "u", "s", "app.kellyacademy.com.br").validar())
                .anyMatch(e -> e.contains("URL completa"));
    }

    @Test
    void toStringNaoMostraCredenciais() {
        var config = comEmail("587", "kelly@gmail.com", "senha-secreta-123", "https://a.com");

        assertThat(config.toString())
                .doesNotContain("senha-secreta-123")
                .doesNotContain("kelly@gmail.com");
        assertThat(config.resumoSeguro())
                .doesNotContain("senha-secreta-123")
                .doesNotContain("kelly@gmail.com");
    }
}
