package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.application.usecase.LogSeguro;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Chamada ao generateContent do Gemini, compartilhada pelos adapters de IA.
 * Cuida do HTTP, do envelope da resposta e das novas tentativas; quem chama
 * só monta as partes (prompt, imagens) ou a conversa e interpreta o texto gerado.
 *
 * <p>Não é @Component: o {@link GeminiConfig} cria uma instância por chave de API
 * (correção/transcrição e Júlia, cada uma no seu projeto do Google Cloud).
 */
class GeminiClient {
    static final int MAX_TENTATIVAS = 2;
    /** Valores aceitos em generationConfig.thinkingConfig.thinkingLevel (Gemini 3.x). */
    static final Set<String> NIVEIS_RACIOCINIO = Set.of("minimal", "low", "medium", "high");
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final RestClient restClient;
    private final JsonMapper json;
    private final GeminiEnvelopeParser envelopeParser;
    private final String apiKey;

    GeminiClient(String baseUrl, String apiKey, int timeoutSegundos) {
        this.apiKey = apiKey;
        this.json = JsonMapper.builder().build();
        this.envelopeParser = new GeminiEnvelopeParser(json);

        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Envia as partes ao modelo e devolve o que {@code interpretar} produzir a partir da resposta.
     * Repete até {@link #MAX_TENTATIVAS} vezes em falha passageira ou resposta mal formada,
     * inclusive quando a própria interpretação lança {@link GeminiException} repetível.
     * Qualquer outra exceção da interpretação sobe na hora, sem nova tentativa.
     *
     * @throws GeminiException quando as tentativas acabam ou a falha não vale repetir
     */
    <T> T gerar(String modelo, double temperatura, List<GeminiParte> partes, Function<GeminiResposta, T> interpretar) {
        return gerar(modelo, temperatura, null, partes, interpretar);
    }

    /**
     * Igual ao {@link #gerar(String, double, List, Function)}, com o nível de raciocínio do modelo
     * ({@code minimal}, {@code low}, {@code medium} ou {@code high}). Nulo ou vazio = padrão do modelo.
     */
    <T> T gerar(String modelo, double temperatura, String raciocinio, List<GeminiParte> partes,
                Function<GeminiResposta, T> interpretar) {
        exigirModelo(modelo);
        if (partes == null || partes.isEmpty()) {
            throw new IllegalArgumentException("a requisição ao Gemini precisa de ao menos uma parte");
        }
        return enviar(modelo, montarCorpo(partes, temperatura, raciocinio), interpretar);
    }

    /**
     * Conversa com instrução de sistema, histórico e resposta em texto livre (Júlia).
     * Mesmas regras de nova tentativa do {@link #gerar}.
     */
    <T> T conversar(String modelo, double temperatura, String raciocinio, int maxTokensSaida,
                    GeminiConversa conversa, Function<GeminiResposta, T> interpretar) {
        exigirModelo(modelo);
        Objects.requireNonNull(conversa, "conversa é obrigatória");
        return enviar(modelo, montarCorpoConversa(conversa, temperatura, raciocinio, maxTokensSaida), interpretar);
    }

    String montarCorpo(List<GeminiParte> partes, double temperatura) {
        return montarCorpo(partes, temperatura, null);
    }

    String montarCorpo(List<GeminiParte> partes, double temperatura, String raciocinio) {
        ObjectNode corpo = json.createObjectNode();
        ArrayNode nos = corpo.putArray("contents")
                .addObject()
                .put("role", "user")
                .putArray("parts");
        for (GeminiParte parte : partes) {
            if (parte instanceof GeminiParte.Texto texto) {
                nos.addObject().put("text", texto.texto());
            } else if (parte instanceof GeminiParte.Imagem imagem) {
                nos.addObject()
                        .putObject("inlineData")
                        .put("mimeType", imagem.mimeType())
                        .put("data", imagem.dadosBase64());
            }
        }
        ObjectNode configuracao = corpo.putObject("generationConfig")
                .put("temperature", temperatura)
                .put("responseMimeType", "application/json");
        adicionarRaciocinio(configuracao, raciocinio);
        return json.writeValueAsString(corpo);
    }

    String montarCorpoConversa(GeminiConversa conversa, double temperatura, String raciocinio, int maxTokensSaida) {
        if (maxTokensSaida <= 0) {
            throw new IllegalArgumentException("maxTokensSaida deve ser positivo");
        }
        ObjectNode corpo = json.createObjectNode();
        // Instrução de sistema + contexto ficam no começo e não mudam entre as perguntas da mesma conversa:
        // prefixo estável favorece o cache implícito do Gemini.
        corpo.putObject("systemInstruction")
                .putArray("parts")
                .addObject()
                .put("text", conversa.instrucaoSistema());
        ArrayNode conteudos = corpo.putArray("contents");
        for (GeminiTurno turno : conversa.turnos()) {
            conteudos.addObject()
                    .put("role", turno.papel().valorApi())
                    .putArray("parts")
                    .addObject()
                    .put("text", turno.texto());
        }
        ObjectNode configuracao = corpo.putObject("generationConfig")
                .put("temperature", temperatura)
                .put("maxOutputTokens", maxTokensSaida)
                .put("responseMimeType", "text/plain");
        adicionarRaciocinio(configuracao, raciocinio);
        return json.writeValueAsString(corpo);
    }

    private <T> T enviar(String modelo, String corpo, Function<GeminiResposta, T> interpretar) {
        GeminiException ultimaFalha = null;

        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                String respostaHttp = restClient.post()
                        .uri("/models/{modelo}:generateContent", modelo)
                        .header("x-goog-api-key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(corpo)
                        .retrieve()
                        .body(String.class);
                return interpretar.apply(envelopeParser.ler(respostaHttp, modelo));
            } catch (HttpClientErrorException e) {
                // 4xx (chave inválida, cota estourada, requisição malformada): repetir não resolve
                throw GeminiException.definitiva(
                        "Gemini recusou a requisição (HTTP " + e.getStatusCode().value() + ")", e
                );
            } catch (HttpServerErrorException | ResourceAccessException e) {
                ultimaFalha = GeminiException.repetivel("Gemini indisponível: " + e.getMessage(), e);
            } catch (GeminiException e) {
                if (!e.podeRepetir()) {
                    throw e;
                }
                ultimaFalha = e;
            }
            log.warn("Falha na chamada ao Gemini {} (tentativa {}/{}): {}",
                    modelo, tentativa, MAX_TENTATIVAS, LogSeguro.limpar(ultimaFalha.getMessage()));
        }
        throw ultimaFalha;
    }

    private static void adicionarRaciocinio(ObjectNode configuracao, String raciocinio) {
        if (raciocinio == null || raciocinio.isBlank()) {
            return;
        }
        String nivel = raciocinio.strip().toLowerCase(Locale.ROOT);
        if (!NIVEIS_RACIOCINIO.contains(nivel)) {
            throw new IllegalArgumentException("nível de raciocínio inválido: " + raciocinio);
        }
        configuracao.putObject("thinkingConfig").put("thinkingLevel", nivel);
    }

    private static void exigirModelo(String modelo) {
        if (modelo == null || modelo.isBlank()) {
            throw new IllegalArgumentException("modelo é obrigatório");
        }
    }
}
