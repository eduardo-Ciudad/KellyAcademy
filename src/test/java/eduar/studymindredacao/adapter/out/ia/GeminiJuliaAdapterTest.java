package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.julia.CompetenciaContexto;
import eduar.studymindredacao.domain.julia.ContextoJulia;
import eduar.studymindredacao.domain.julia.JuliaIndisponivelException;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiJuliaAdapterTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant AGORA = Instant.parse("2026-10-21T20:00:00Z");

    private ServidorGeminiFalso gemini;
    private GeminiJuliaAdapter adapter;

    @BeforeEach
    void subirServidor() throws IOException {
        gemini = ServidorGeminiFalso.iniciar();
        var client = new GeminiClient(gemini.baseUrl(), "chave-julia", 5);
        adapter = new GeminiJuliaAdapter(new PromptJuliaBuilder(), client, "modelo-julia", 0.4, "low", 1024);
    }

    @AfterEach
    void derrubarServidor() {
        gemini.close();
    }

    @Test
    void enviaHistoricoEPerguntaComOsPapeisCertosEAInstrucaoComOContexto() {
        gemini.responder(200, envelope("Para subir a C3, defenda a tese com..."));
        var historico = List.of(
                MensagemJulia.doUsuario("Por que perdi pontos na C3?", 500, AGORA),
                MensagemJulia.daJulia(new RespostaJulia("Na C3 seus argumentos...", 900, 120), AGORA)
        );

        adapter.responder(contexto(), historico, "E como eu melhoro?");

        var requisicao = gemini.recebidas().get(0);
        assertThat(requisicao.apiKey()).isEqualTo("chave-julia");
        assertThat(requisicao.caminho()).isEqualTo("/v1beta/models/modelo-julia:generateContent");

        JsonNode corpo = JSON.readTree(requisicao.corpo());
        assertThat(corpo.path("systemInstruction").path("parts").path(0).path("text").asString())
                .contains("Você é a Júlia")
                .contains("<tema>Desafios da educação no Brasil</tema>");
        JsonNode conteudos = corpo.path("contents");
        assertThat(conteudos).hasSize(3);
        assertThat(conteudos.path(0).path("role").asString()).isEqualTo("user");
        assertThat(conteudos.path(0).path("parts").path(0).path("text").asString())
                .isEqualTo("Por que perdi pontos na C3?");
        assertThat(conteudos.path(1).path("role").asString()).isEqualTo("model");
        assertThat(conteudos.path(2).path("parts").path(0).path("text").asString()).isEqualTo("E como eu melhoro?");
        assertThat(corpo.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(1024);
    }

    @Test
    void primeiraPerguntaVaiSemHistorico() {
        gemini.responder(200, envelope("Olá! Na C3..."));

        adapter.responder(contexto(), List.of(), "Por que perdi pontos na C3?");

        JsonNode corpo = JSON.readTree(gemini.recebidas().get(0).corpo());
        assertThat(corpo.path("contents")).hasSize(1);
    }

    @Test
    void devolveTextoETokensDaResposta() {
        gemini.responder(200, envelope("Na C3 você perdeu pontos porque...", 300));

        RespostaJulia resposta = adapter.responder(contexto(), List.of(), "Por que perdi pontos na C3?");

        assertThat(resposta.texto()).isEqualTo("Na C3 você perdeu pontos porque...");
        assertThat(resposta.tokensEntrada()).isEqualTo(2100);
        assertThat(resposta.tokensSaida()).isEqualTo(650 + 300);
    }

    @Test
    void recusaDoGeminiViraJuliaIndisponivel() {
        gemini.responder(429, "{}");

        assertThatThrownBy(() -> adapter.responder(contexto(), List.of(), "Por que perdi pontos?"))
                .isInstanceOf(JuliaIndisponivelException.class)
                .hasMessage(GeminiJuliaAdapter.MENSAGEM_INDISPONIVEL);
        assertThat(gemini.recebidas()).hasSize(1);
    }

    @Test
    void geminiForaDoArDepoisDasTentativasViraJuliaIndisponivel() {
        gemini.responder(500, "{}");
        gemini.responder(500, "{}");

        assertThatThrownBy(() -> adapter.responder(contexto(), List.of(), "Por que perdi pontos?"))
                .isInstanceOf(JuliaIndisponivelException.class);
        assertThat(gemini.recebidas()).hasSize(GeminiClient.MAX_TENTATIVAS);
    }

    private static ContextoJulia contexto() {
        return new ContextoJulia("Desafios da educação no Brasil", "Texto da redação.", 600, List.of(
                new CompetenciaContexto(1, 160, "poucos desvios", List.of()),
                new CompetenciaContexto(2, 120, "repertório de bolso", List.of()),
                new CompetenciaContexto(3, 80, "argumentos previsíveis", List.of("tese sem defesa")),
                new CompetenciaContexto(4, 120, "", List.of()),
                new CompetenciaContexto(5, 120, "falta detalhamento", List.of())
        ), null);
    }
}
