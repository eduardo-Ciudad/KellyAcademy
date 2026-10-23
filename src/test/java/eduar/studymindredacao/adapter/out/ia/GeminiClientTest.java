package eduar.studymindredacao.adapter.out.ia;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eduar.studymindredacao.domain.exception.ImagemNaoReconhecidaException;
import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sobe um servidor HTTP local que imita o Gemini, sem chamar a API real. */
class GeminiClientTest {
    private record RespostaFalsa(int status, String corpo, long atrasoMs) {
        RespostaFalsa(int status, String corpo) {
            this(status, corpo, 0);
        }
    }

    private record RequisicaoRecebida(String caminho, String apiKey, String corpo) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<GeminiParte> SO_TEXTO = List.of(new GeminiParte.Texto("prompt de teste"));
    private static final Function<GeminiResposta, String> TEXTO_GERADO = GeminiResposta::textoGerado;

    private HttpServer servidor;
    private final Deque<RespostaFalsa> respostas = new ArrayDeque<>();
    private final List<RequisicaoRecebida> recebidas = new ArrayList<>();
    private GeminiClient client;

    @BeforeEach
    void subirServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta";
        client = new GeminiClient(baseUrl, "chave-de-teste", 5);
    }

    @AfterEach
    void derrubarServidor() {
        servidor.stop(0);
    }

    private void responder(HttpExchange troca) throws IOException {
        String corpo = new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        recebidas.add(new RequisicaoRecebida(
                troca.getRequestURI().getPath(), troca.getRequestHeaders().getFirst("x-goog-api-key"), corpo
        ));
        RespostaFalsa resposta = respostas.isEmpty() ? new RespostaFalsa(500, "{}") : respostas.poll();
        if (resposta.atrasoMs() > 0) {
            try {
                Thread.sleep(resposta.atrasoMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        byte[] bytes = resposta.corpo().getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(resposta.status(), bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }

    @Test
    void enviaAsPartesNaOrdemComChaveNoHeaderEConfiguracao() {
        respostas.add(new RespostaFalsa(200, envelope("{\"ok\":true}")));
        var imagem = GeminiParte.Imagem.de("image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        client.gerar("gemini-teste", 0.0, List.of(imagem, new GeminiParte.Texto("transcreva")), TEXTO_GERADO);

        var requisicao = recebidas.get(0);
        assertThat(requisicao.caminho()).isEqualTo("/v1beta/models/gemini-teste:generateContent");
        assertThat(requisicao.apiKey()).isEqualTo("chave-de-teste");
        assertThat(requisicao.corpo()).doesNotContain("chave-de-teste");

        JsonNode corpo = JSON.readTree(requisicao.corpo());
        JsonNode partes = corpo.path("contents").path(0).path("parts");
        assertThat(corpo.path("contents").path(0).path("role").asString()).isEqualTo("user");
        assertThat(partes).hasSize(2);
        assertThat(partes.path(0).path("inlineData").path("mimeType").asString()).isEqualTo("image/jpeg");
        assertThat(partes.path(0).path("inlineData").path("data").asString()).isEqualTo(imagem.dadosBase64());
        assertThat(partes.path(1).path("text").asString()).isEqualTo("transcreva");
        assertThat(corpo.path("generationConfig").path("temperature").asDouble()).isEqualTo(0.0);
        assertThat(corpo.path("generationConfig").path("responseMimeType").asString()).isEqualTo("application/json");
    }

    @Test
    void devolveOQueAInterpretacaoProduz() {
        respostas.add(new RespostaFalsa(200, envelope("{\"ok\":true}")));

        Integer tokens = client.gerar("gemini-teste", 0.3, SO_TEXTO, GeminiResposta::tokensEntrada);

        assertThat(tokens).isEqualTo(2100);
    }

    @Test
    void repeteUmaVezQuandoOGeminiDaErro500() {
        respostas.add(new RespostaFalsa(503, "{\"error\":\"indisponivel\"}"));
        respostas.add(new RespostaFalsa(200, envelope("{\"ok\":true}")));

        assertThat(client.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO)).isEqualTo("{\"ok\":true}");
        assertThat(recebidas).hasSize(2);
    }

    @Test
    void repeteQuandoAInterpretacaoFalhaComErroRepetivel() {
        respostas.add(new RespostaFalsa(200, envelope("{\"incompleto\":true}")));
        respostas.add(new RespostaFalsa(200, envelope("{\"ok\":true}")));
        var chamadas = new AtomicInteger();

        String resultado = client.gerar("gemini-teste", 0.3, SO_TEXTO, resposta -> {
            if (chamadas.incrementAndGet() == 1) {
                throw GeminiException.repetivel("conteúdo inconsistente");
            }
            return resposta.textoGerado();
        });

        assertThat(resultado).isEqualTo("{\"ok\":true}");
        assertThat(recebidas).hasSize(2);
    }

    @Test
    void desisteDepoisDasTentativasComAUltimaFalha() {
        respostas.add(new RespostaFalsa(200, "{\"candidates\":[]}"));
        respostas.add(new RespostaFalsa(200, "{\"candidates\":[]}"));

        assertThatThrownBy(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO))
                .isInstanceOf(GeminiException.class)
                .hasMessageContaining("sem candidatos");
        assertThat(recebidas).hasSize(GeminiClient.MAX_TENTATIVAS);
    }

    @Test
    void naoRepeteErro4xx() {
        respostas.add(new RespostaFalsa(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));

        assertThatThrownBy(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO))
                .isInstanceOfSatisfying(GeminiException.class, e -> assertThat(e.podeRepetir()).isFalse())
                .hasMessageContaining("HTTP 429");
        assertThat(recebidas).hasSize(1);
    }

    @Test
    void naoRepeteFalhaDefinitivaDaInterpretacao() {
        respostas.add(new RespostaFalsa(200, envelope("{\"ok\":true}")));

        assertThatThrownBy(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, resposta -> {
            throw GeminiException.definitiva("conteúdo proibido", null);
        })).isInstanceOf(GeminiException.class).hasMessage("conteúdo proibido");
        assertThat(recebidas).hasSize(1);
    }

    @Test
    void excecaoQueNaoEDoGeminiSobeSemNovaTentativa() {
        respostas.add(new RespostaFalsa(200, envelope("{\"eh_redacao\":false}")));

        assertThatThrownBy(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, resposta -> {
            throw new IllegalStateException("não é redação");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(recebidas).hasSize(1);
    }

    // ---------- custo da falha (6.5): decide se a vaga volta ----------

    private static final String SEM_CANDIDATOS = "{\"candidates\":[]}";

    private static void custoDaFalha(Runnable chamada, CustoFalhaIA esperado) {
        assertThatThrownBy(chamada::run)
                .isInstanceOfSatisfying(GeminiException.class, e -> assertThat(e.custo()).isEqualTo(esperado));
    }

    @Test
    void erro5xxNasDuasTentativasNaoECobrado() {
        respostas.add(new RespostaFalsa(503, "{}"));
        respostas.add(new RespostaFalsa(500, "{}"));

        custoDaFalha(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO), CustoFalhaIA.NAO_COBRADA);
        assertThat(recebidas).hasSize(2);
    }

    @Test
    void erro4xxNaoECobradoENaoRepete() {
        respostas.add(new RespostaFalsa(429, "{}"));

        custoDaFalha(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO), CustoFalhaIA.NAO_COBRADA);
        assertThat(recebidas).hasSize(1);
    }

    @Test
    void respostaInvalidaNasDuasTentativasECobrada() {
        respostas.add(new RespostaFalsa(200, SEM_CANDIDATOS));
        respostas.add(new RespostaFalsa(200, SEM_CANDIDATOS));

        custoDaFalha(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO), CustoFalhaIA.COBRADA);
    }

    @Test
    void respostaInvalidaSeguidaDe5xxContinuaCobrada() {
        // Só a última tentativa diria "não cobrada": a primeira, cobrada, não pode ser esquecida
        respostas.add(new RespostaFalsa(200, SEM_CANDIDATOS));
        respostas.add(new RespostaFalsa(503, "{}"));

        custoDaFalha(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO), CustoFalhaIA.COBRADA);
        assertThat(recebidas).hasSize(2);
    }

    @Test
    void erro5xxSeguidoDeRespostaInvalidaECobrada() {
        respostas.add(new RespostaFalsa(503, "{}"));
        respostas.add(new RespostaFalsa(200, SEM_CANDIDATOS));

        custoDaFalha(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO), CustoFalhaIA.COBRADA);
    }

    @Test
    void falhaDefinitivaDaInterpretacaoECobrada() {
        respostas.add(new RespostaFalsa(200, envelope("{\"ok\":true}")));

        custoDaFalha(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, resposta -> {
            throw GeminiException.definitiva("conteúdo proibido", null);
        }), CustoFalhaIA.COBRADA);
    }

    @Test
    void timeoutDeLeituraEIncerto() {
        var comTimeoutCurto = new GeminiClient(
                "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta", "chave-de-teste", 1);
        respostas.add(new RespostaFalsa(200, envelope("{\"ok\":true}"), 1500));
        respostas.add(new RespostaFalsa(200, envelope("{\"ok\":true}"), 1500));

        custoDaFalha(() -> comTimeoutCurto.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO), CustoFalhaIA.INCERTA);
    }

    @Test
    void servidorForaDoArNaoECobrado() throws IOException {
        int portaFechada;
        try (var socket = new ServerSocket(0)) {
            portaFechada = socket.getLocalPort();
        }
        var semServidor = new GeminiClient("http://127.0.0.1:" + portaFechada + "/v1beta", "chave-de-teste", 5);

        custoDaFalha(() -> semServidor.gerar("gemini-teste", 0.3, SO_TEXTO, TEXTO_GERADO), CustoFalhaIA.NAO_COBRADA);
    }

    @Test
    void excecaoDeDominioDaInterpretacaoSobeIntactaECobrada() {
        respostas.add(new RespostaFalsa(200, envelope("{\"eh_redacao\":false}")));
        var naoEhRedacao = new ImagemNaoReconhecidaException();

        assertThatThrownBy(() -> client.gerar("gemini-teste", 0.3, SO_TEXTO, resposta -> {
            throw naoEhRedacao;
        })).isSameAs(naoEhRedacao);
        assertThat(naoEhRedacao.custo()).isEqualTo(CustoFalhaIA.COBRADA);
        assertThat(recebidas).hasSize(1);
    }

    @Test
    void rejeitaRequisicaoSemPartesOuSemModelo() {
        assertThatThrownBy(() -> client.gerar("gemini-teste", 0.3, List.of(), TEXTO_GERADO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.gerar(" ", 0.3, SO_TEXTO, TEXTO_GERADO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(recebidas).isEmpty();
    }

    @Test
    void corpoSemRaciocinioNaoEnviaThinkingConfig() {
        JsonNode corpo = JsonMapper.builder().build()
                .readTree(client.montarCorpo(List.of(new GeminiParte.Texto("oi")), 0.3, null));

        assertThat(corpo.path("generationConfig").has("thinkingConfig")).isFalse();
    }

    @Test
    void corpoComRaciocinioEnviaThinkingLevel() {
        JsonNode corpo = JsonMapper.builder().build()
                .readTree(client.montarCorpo(List.of(new GeminiParte.Texto("oi")), 0.3, " Medium "));

        assertThat(corpo.path("generationConfig").path("thinkingConfig").path("thinkingLevel").asString(""))
                .isEqualTo("medium");
    }

    @Test
    void rejeitaNivelDeRaciocinioInvalido() {
        assertThatThrownBy(() -> client.montarCorpo(List.of(new GeminiParte.Texto("oi")), 0.3, "turbo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("raciocínio");
    }
}
