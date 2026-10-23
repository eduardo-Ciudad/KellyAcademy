package eduar.studymindredacao.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChavesGeminiTest {
    private static final String CORRECAO = "chave-correcao-123";
    private static final String JULIA = "chave-julia-456";
    private static final String TRANSCRICAO = "chave-transcricao-789";

    @Test
    void tresChavesDiferentesSaoValidas() {
        assertThat(new ChavesGemini(CORRECAO, JULIA, TRANSCRICAO).validar()).isEmpty();
    }

    @Test
    void chaveAusenteOuEmBrancoCitaONomeDaVariavel() {
        var erros = new ChavesGemini(null, "  ", "").validar();

        assertThat(erros).containsExactlyInAnyOrder(
                "GEMINI_API_KEY é obrigatória",
                "JULIA_GEMINI_API_KEY é obrigatória",
                "TRANSCRICAO_GEMINI_API_KEY é obrigatória"
        );
    }

    @Test
    void transcricaoIgualACorrecaoDerrubaASubida() {
        var erros = new ChavesGemini(CORRECAO, JULIA, CORRECAO).validar();

        assertThat(erros).singleElement().asString()
                .startsWith("TRANSCRICAO_GEMINI_API_KEY é igual à GEMINI_API_KEY");
    }

    @Test
    void transcricaoIgualAJuliaDerrubaASubida() {
        var erros = new ChavesGemini(CORRECAO, JULIA, JULIA).validar();

        assertThat(erros).singleElement().asString()
                .startsWith("TRANSCRICAO_GEMINI_API_KEY é igual à JULIA_GEMINI_API_KEY");
    }

    @Test
    void juliaIgualACorrecaoDerrubaASubida() {
        var erros = new ChavesGemini(CORRECAO, CORRECAO, TRANSCRICAO).validar();

        assertThat(erros).singleElement().asString()
                .startsWith("JULIA_GEMINI_API_KEY é igual à GEMINI_API_KEY");
    }

    @Test
    void espacoNasPontasNaoTornaAChaveDiferente() {
        var erros = new ChavesGemini(CORRECAO, JULIA, "  " + CORRECAO + "\n").validar();

        assertThat(erros).singleElement().asString().contains("é igual à GEMINI_API_KEY");
    }

    @Test
    void tresChavesIguaisApontamTodosOsPares() {
        assertThat(new ChavesGemini(CORRECAO, CORRECAO, CORRECAO).validar()).hasSize(3);
    }

    @Test
    void chaveAusenteNaoGeraErroDeIgualdade() {
        var erros = new ChavesGemini(CORRECAO, JULIA, null).validar();

        assertThat(erros).containsExactly("TRANSCRICAO_GEMINI_API_KEY é obrigatória");
    }

    @Test
    void toStringNuncaMostraOsValores() {
        String texto = new ChavesGemini(CORRECAO, JULIA, null).toString();

        assertThat(texto)
                .doesNotContain(CORRECAO, JULIA)
                .contains("correção configurada", "Júlia configurada", "transcrição ausente");
    }

    @Test
    void mensagensDeErroNuncaMostramOsValores() {
        var erros = new ChavesGemini(CORRECAO, CORRECAO, CORRECAO).validar();

        assertThat(erros).allSatisfy(erro -> assertThat(erro).doesNotContain(CORRECAO));
    }
}
