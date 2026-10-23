package eduar.studymindredacao.adapter.out.ia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiConfigTest {
    private static final String URL = "http://localhost:9999/v1beta";

    private final GeminiConfig config = new GeminiConfig();

    @Test
    void transcricaoRecusaChaveVazia() {
        assertThatThrownBy(() -> config.geminiClientTranscricao(URL, "   ", 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TRANSCRICAO_GEMINI_API_KEY não configurada");
    }

    @Test
    void transcricaoRecusaChaveNula() {
        assertThatThrownBy(() -> config.geminiClientTranscricao(URL, null, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("TRANSCRICAO_GEMINI_API_KEY não configurada");
    }

    @Test
    void transcricaoComChaveCriaOCliente() {
        assertThat(config.geminiClientTranscricao(URL, "chave-transcricao", 60)).isNotNull();
    }

    @Test
    void juliaContinuaRecusandoChaveVazia() {
        assertThatThrownBy(() -> config.geminiClientJulia(URL, "", 30))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JULIA_GEMINI_API_KEY não configurada");
    }

    @Test
    void cadaRecursoGanhaOProprioCliente() {
        var correcao = config.geminiClient(URL, "chave-correcao", 60);
        var julia = config.geminiClientJulia(URL, "chave-julia", 30);
        var transcricao = config.geminiClientTranscricao(URL, "chave-transcricao", 60);

        assertThat(transcricao).isNotSameAs(correcao).isNotSameAs(julia);
    }
}
