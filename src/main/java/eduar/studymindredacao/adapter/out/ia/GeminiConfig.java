package eduar.studymindredacao.adapter.out.ia;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Um GeminiClient por chave de API.
 * O principal (correção e transcrição) é @Primary: quem injeta GeminiClient sem qualificador recebe ele.
 * A Júlia pede o dela pelo nome "geminiClientJulia".
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
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("JULIA_GEMINI_API_KEY não configurada");
        }
        return new GeminiClient(baseUrl, apiKey, timeoutSegundos);
    }
}
