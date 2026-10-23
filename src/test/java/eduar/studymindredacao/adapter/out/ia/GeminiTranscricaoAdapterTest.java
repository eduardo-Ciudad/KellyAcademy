package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.exception.ImagemNaoReconhecidaException;
import eduar.studymindredacao.domain.exception.TranscricaoIAException;
import eduar.studymindredacao.domain.model.ImagemRedacao;
import eduar.studymindredacao.domain.model.ImagensDeTeste;
import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiTranscricaoAdapterTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TRANSCRICAO_VALIDA = """
            {"eh_redacao": true, "motivo": null, "linhas": ["A educação no Brasil", "enfrenta [ilegível] desafios."]}
            """;

    private ServidorGeminiFalso gemini;
    private GeminiTranscricaoAdapter adapter;
    private final ImagemRedacao foto = ImagemRedacao.de(ImagensDeTeste.jpeg());

    @BeforeEach
    void subirServidor() throws IOException {
        gemini = ServidorGeminiFalso.iniciar();
        var client = new GeminiClient(gemini.baseUrl(), "chave-de-teste", 5);
        adapter = new GeminiTranscricaoAdapter(new PromptTranscricaoBuilder(), client, "gemini-transcricao-teste", 0.0);
    }

    @AfterEach
    void derrubarServidor() {
        gemini.close();
    }

    @Test
    void enviaAsFotosNaOrdemAntesDoPromptComTemperaturaZero() {
        gemini.responder(200, envelope(TRANSCRICAO_VALIDA));
        var segundaFoto = ImagemRedacao.de(ImagensDeTeste.png());

        adapter.transcrever(List.of(foto, segundaFoto));

        var requisicao = gemini.recebidas().get(0);
        assertThat(requisicao.caminho()).isEqualTo("/v1beta/models/gemini-transcricao-teste:generateContent");

        JsonNode corpo = JSON.readTree(requisicao.corpo());
        JsonNode partes = corpo.path("contents").path(0).path("parts");
        assertThat(partes).hasSize(3);
        assertThat(partes.path(0).path("inlineData").path("mimeType").asString()).isEqualTo("image/jpeg");
        assertThat(Base64.getDecoder().decode(partes.path(0).path("inlineData").path("data").asString()))
                .isEqualTo(ImagensDeTeste.jpeg());
        assertThat(partes.path(1).path("inlineData").path("mimeType").asString()).isEqualTo("image/png");
        assertThat(partes.path(2).path("text").asString()).contains("receberá 2 imagens").contains("NÃO corrija");
        assertThat(corpo.path("generationConfig").path("temperature").asDouble()).isEqualTo(0.0);
    }

    @Test
    void devolveATranscricaoLidaDaResposta() {
        gemini.responder(200, envelope(TRANSCRICAO_VALIDA));

        var resultado = adapter.transcrever(List.of(foto));

        assertThat(resultado.linhas()).containsExactly("A educação no Brasil", "enfrenta [ilegível] desafios.");
        assertThat(resultado.linhasComTrechoIlegivel()).containsExactly(2);
        assertThat(resultado.modelo()).isEqualTo("gemini-teste-001");
        assertThat(resultado.tokensEntrada()).isEqualTo(2100);
    }

    @Test
    void fotoSemRedacaoNaoERepetida() {
        gemini.responder(200, envelope("{\"eh_redacao\": false, \"motivo\": \"foto de um gato\", \"linhas\": []}"));

        assertThatThrownBy(() -> adapter.transcrever(List.of(foto)))
                .isInstanceOf(ImagemNaoReconhecidaException.class);
        assertThat(gemini.recebidas()).hasSize(1);
    }

    @Test
    void repeteQuandoARespostaVemInvalida() {
        gemini.responder(200, envelope("isto não é json"));
        gemini.responder(200, envelope(TRANSCRICAO_VALIDA));

        assertThat(adapter.transcrever(List.of(foto)).qtdLinhasEscritas()).isEqualTo(2);
        assertThat(gemini.recebidas()).hasSize(2);
    }

    @Test
    void falhaDoGeminiViraTranscricaoIAException() {
        gemini.responder(200, envelope("lixo"));
        gemini.responder(200, envelope("lixo de novo"));

        assertThatThrownBy(() -> adapter.transcrever(List.of(foto)))
                .isInstanceOf(TranscricaoIAException.class)
                .hasMessageContaining("não é JSON")
                .hasCauseInstanceOf(GeminiException.class);
        assertThat(gemini.recebidas()).hasSize(GeminiClient.MAX_TENTATIVAS);
    }

    @Test
    void erro4xxViraTranscricaoIAExceptionSemRepetir() {
        gemini.responder(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}");

        assertThatThrownBy(() -> adapter.transcrever(List.of(foto)))
                .isInstanceOf(TranscricaoIAException.class)
                .hasMessageContaining("HTTP 400");
        assertThat(gemini.recebidas()).hasSize(1);
    }

    @Test
    void geminiForaDoArChegaComoNaoCobrada() {
        gemini.responder(503, "{}");
        gemini.responder(503, "{}");

        assertThatThrownBy(() -> adapter.transcrever(List.of(foto)))
                .isInstanceOfSatisfying(TranscricaoIAException.class,
                        e -> assertThat(e.custo()).isEqualTo(CustoFalhaIA.NAO_COBRADA));
    }

    @Test
    void respostaInvalidaChegaComoCobrada() {
        gemini.responder(200, envelope("lixo"));
        gemini.responder(200, envelope("lixo de novo"));

        assertThatThrownBy(() -> adapter.transcrever(List.of(foto)))
                .isInstanceOfSatisfying(TranscricaoIAException.class,
                        e -> assertThat(e.custo()).isEqualTo(CustoFalhaIA.COBRADA));
    }

    @Test
    void fotoQueNaoERedacaoChegaComoCobrada() {
        gemini.responder(200, envelope("{\"eh_redacao\": false, \"motivo\": \"foto de um gato\", \"linhas\": []}"));

        assertThatThrownBy(() -> adapter.transcrever(List.of(foto)))
                .isInstanceOfSatisfying(ImagemNaoReconhecidaException.class,
                        e -> assertThat(e.custo()).isEqualTo(CustoFalhaIA.COBRADA));
    }

    @Test
    void rejeitaListaSemImagens() {
        assertThatThrownBy(() -> adapter.transcrever(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(gemini.recebidas()).isEmpty();
    }
}