package eduar.studymindredacao.adapter.out.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Um GeminiClient por chave de API, e cada chave de um projeto próprio no Google Cloud
 * (isolamento de cota, custo e revogação).
 * O principal (correção) é @Primary: quem injeta GeminiClient sem qualificador recebe ele.
 * A Júlia pede o dela pelo nome "geminiClientJulia" e a transcrição pelo nome "geminiClientTranscricao".
 * Que as três chaves são diferentes entre si é conferido na subida pelo ValidadorConfiguracao.
 */
@Configuration
class GeminiConfig {

    @Bean
    @Primary
    GeminiClient geminiClient(
            @Value("${gemini.base-url}") String baseUrl,
            @Value("${gemini.api-key}") String apiKey,
            @Value("${gemini.timeout-segundos}") int timeoutSegundos
    ) {
        return new GeminiClient(baseUrl, apiKey, timeoutSegundos);
    }

    @Bean
    GeminiClient geminiClientJulia(
            @Value("${gemini.base-url}") String baseUrl,
            @Value("${julia.gemini.api-key}") String apiKey,
            @Value("${julia.gemini.timeout-segundos}") int timeoutSegundos
    ) {
        exigirChave(apiKey, "JULIA_GEMINI_API_KEY");
        return new GeminiClient(baseUrl, apiKey, timeoutSegundos);
    }

    /** Sem cair na chave principal: se faltar a chave própria, a API não sobe em vez de perder o isolamento calada. */
    @Bean
    GeminiClient geminiClientTranscricao(
            @Value("${gemini.base-url}") String baseUrl,
            @Value("${transcricao.gemini.api-key}") String apiKey,
            @Value("${transcricao.gemini.timeout-segundos}") int timeoutSegundos
    ) {
        exigirChave(apiKey, "TRANSCRICAO_GEMINI_API_KEY");
        return new GeminiClient(baseUrl, apiKey, timeoutSegundos);
    }

    private static void exigirChave(String apiKey, String variavel) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(variavel + " não configurada");
        }
    }
}
