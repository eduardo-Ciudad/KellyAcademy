package eduar.studymindredacao.adapter.out.ia;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;

import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiClientConversaTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ServidorGeminiFalso gemini;
    private GeminiClient client;

    @BeforeEach
    void subirServidor() throws IOException {
        gemini = ServidorGeminiFalso.iniciar();
        client = new GeminiClient(gemini.baseUrl(), "chave-julia", 5);
    }

    @AfterEach
    void derrubarServidor() {
        gemini.close();
    }

    @Test
    void corpoTemInstrucaoDeSistemaTurnosAlternadosERespostaEmTexto() {
        JsonNode corpo = JSON.readTree(client.montarCorpoConversa(conversa(), 0.4, "low", 1024));

        assertThat(corpo.path("systemInstruction").path("parts").path(0).path("text").asString())
                .isEqualTo("Você é a Júlia.");
        JsonNode conteudos = corpo.path("contents");
        assertThat(conteudos).hasSize(3);
        assertThat(conteudos.path(0).path("role").asString()).isEqualTo("user");
        assertThat(conteudos.path(1).path("role").asString()).isEqualTo("model");
        assertThat(conteudos.path(2).path("role").asString()).isEqualTo("user");
        assertThat(conteudos.path(2).path("parts").path(0).path("text").asString()).isEqualTo("E como melhoro?");

        JsonNode configuracao = corpo.path("generationConfig");
        assertThat(configuracao.path("temperature").asDouble()).isEqualTo(0.4);
        assertThat(configuracao.path("maxOutputTokens").asInt()).isEqualTo(1024);
        assertThat(configuracao.path("responseMimeType").asString()).isEqualTo("text/plain");
        assertThat(configuracao.path("thinkingConfig").path("thinkingLevel").asString()).isEqualTo("low");
    }

    @Test
    void semRaciocinioNaoEnviaThinkingConfig() {
        JsonNode corpo = JSON.readTree(client.montarCorpoConversa(conversa(), 0.4, null, 1024));

        assertThat(corpo.path("generationConfig").has("thinkingConfig")).isFalse();
    }

    @Test
    void maxTokensInvalidoERecusado() {
        assertThatThrownBy(() -> client.montarCorpoConversa(conversa(), 0.4, "low", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void conversarUsaAChaveDesteClientEDevolveOTexto() {
        gemini.responder(200, envelope("Na C3 você perdeu pontos porque..."));

        String texto = client.conversar("modelo-julia", 0.4, "low", 1024, conversa(), GeminiResposta::textoGerado);

        assertThat(texto).isEqualTo("Na C3 você perdeu pontos porque...");
        var requisicao = gemini.recebidas().get(0);
        assertThat(requisicao.apiKey()).isEqualTo("chave-julia");
        assertThat(requisicao.caminho()).isEqualTo("/v1beta/models/modelo-julia:generateContent");
    }

    @Test
    void cotaEstouradaNaoERepetida() {
        gemini.responder(429, "{}");

        assertThatThrownBy(() -> client.conversar("modelo-julia", 0.4, "low", 1024, conversa(),
                GeminiResposta::textoGerado))
                .isInstanceOf(GeminiException.class);
        assertThat(gemini.recebidas()).hasSize(1);
    }

    @Test
    void falhaDoServidorERepetidaUmaVez() {
        gemini.responder(500, "{}");
        gemini.responder(200, envelope("resposta"));

        String texto = client.conversar("modelo-julia", 0.4, "low", 1024, conversa(), GeminiResposta::textoGerado);

        assertThat(texto).isEqualTo("resposta");
        assertThat(gemini.recebidas()).hasSize(2);
    }

    private static GeminiConversa conversa() {
        return new GeminiConversa("Você é a Júlia.", List.of(
                GeminiTurno.doUsuario("Por que perdi pontos na C3?"),
                GeminiTurno.doModelo("Na C3..."),
                GeminiTurno.doUsuario("E como melhoro?")
        ));
    }
}
