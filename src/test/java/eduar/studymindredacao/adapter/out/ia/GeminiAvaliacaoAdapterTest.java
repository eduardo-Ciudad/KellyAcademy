package eduar.studymindredacao.adapter.out.ia;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eduar.studymindredacao.domain.exception.AvaliacaoIAException;
import eduar.studymindredacao.domain.model.SolicitacaoAvaliacao;
import eduar.studymindredacao.domain.model.enums.CustoFalhaIA;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.AVALIACAO_VALIDA;
import static eduar.studymindredacao.adapter.out.ia.GeminiRespostas.envelope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sobe um servidor HTTP local que imita o Gemini, sem chamar a API real. */
class GeminiAvaliacaoAdapterTest {
    private record RespostaFalsa(int status, String corpo) {
    }

    private record RequisicaoRecebida(String caminho, String apiKey, String corpo) {
    }

    private HttpServer servidor;
    private final Deque<RespostaFalsa> respostas = new ArrayDeque<>();
    private final List<RequisicaoRecebida> recebidas = new ArrayList<>();
    private GeminiAvaliacaoAdapter adapter;

    @BeforeEach
    void subirServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        String baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta";
        var client = new GeminiClient(baseUrl, "chave-de-teste", 5);
        adapter = new GeminiAvaliacaoAdapter(new PromptAvaliacaoBuilder(), client, "gemini-teste", 0.3);    }

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
        byte[] bytes = resposta.corpo().getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(resposta.status(), bytes.length);
        troca.getResponseBody().write(bytes);
        troca.close();
    }

    private final SolicitacaoAvaliacao solicitacao =
            new SolicitacaoAvaliacao("Democratização do acesso ao cinema no Brasil", "Texto da redação do aluno.");

    @Test
    void enviaPromptComChaveNoHeaderEInterpretaResposta() {
        respostas.add(new RespostaFalsa(200, envelope(AVALIACAO_VALIDA)));

        var resultado = adapter.avaliar(solicitacao);

        assertThat(resultado.notaTotal()).isEqualTo(640);
        assertThat(recebidas).hasSize(1);
        var requisicao = recebidas.get(0);
        assertThat(requisicao.caminho()).isEqualTo("/v1beta/models/gemini-teste:generateContent");
        assertThat(requisicao.apiKey()).isEqualTo("chave-de-teste");
        assertThat(requisicao.corpo())
                .contains("Democratização do acesso ao cinema no Brasil")
                .contains("Texto da redação do aluno.")
                .contains("\"responseMimeType\":\"application/json\"")
                .contains("\"temperature\":0.3")
                .doesNotContain("chave-de-teste");
    }

    @Test
    void repeteUmaVezQuandoOGeminiDaErro500() {
        respostas.add(new RespostaFalsa(503, "{\"error\":\"indisponivel\"}"));
        respostas.add(new RespostaFalsa(200, envelope(AVALIACAO_VALIDA)));

        var resultado = adapter.avaliar(solicitacao);

        assertThat(resultado.notaTotal()).isEqualTo(640);
        assertThat(recebidas).hasSize(2);
    }

    @Test
    void repeteUmaVezQuandoARespostaVemInvalida() {
        respostas.add(new RespostaFalsa(200, envelope("isto não é json")));
        respostas.add(new RespostaFalsa(200, envelope(AVALIACAO_VALIDA)));

        assertThat(adapter.avaliar(solicitacao).notaTotal()).isEqualTo(640);
        assertThat(recebidas).hasSize(2);
    }

    @Test
    void desisteDepoisDeDuasTentativasInvalidas() {
        respostas.add(new RespostaFalsa(200, envelope("lixo")));
        respostas.add(new RespostaFalsa(200, envelope("lixo de novo")));

        assertThatThrownBy(() -> adapter.avaliar(solicitacao))
                .isInstanceOf(AvaliacaoIAException.class)
                .hasMessageContaining("não é JSON");
        assertThat(recebidas).hasSize(GeminiClient.MAX_TENTATIVAS);
    }

    @Test
    void naoRepeteErro4xx() {
        respostas.add(new RespostaFalsa(429, "{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));

        assertThatThrownBy(() -> adapter.avaliar(solicitacao))
                .isInstanceOf(AvaliacaoIAException.class)
                .hasMessageContaining("HTTP 429");
        assertThat(recebidas).hasSize(1);
    }

    @Test
    void geminiForaDoArChegaComoNaoCobrada() {
        respostas.add(new RespostaFalsa(503, "{}"));
        respostas.add(new RespostaFalsa(503, "{}"));

        assertThatThrownBy(() -> adapter.avaliar(solicitacao))
                .isInstanceOfSatisfying(AvaliacaoIAException.class,
                        e -> assertThat(e.custo()).isEqualTo(CustoFalhaIA.NAO_COBRADA));
    }

    @Test
    void respostaInvalidaChegaComoCobrada() {
        respostas.add(new RespostaFalsa(200, envelope("lixo")));
        respostas.add(new RespostaFalsa(200, envelope("lixo de novo")));

        assertThatThrownBy(() -> adapter.avaliar(solicitacao))
                .isInstanceOfSatisfying(AvaliacaoIAException.class,
                        e -> assertThat(e.custo()).isEqualTo(CustoFalhaIA.COBRADA));
    }
}
