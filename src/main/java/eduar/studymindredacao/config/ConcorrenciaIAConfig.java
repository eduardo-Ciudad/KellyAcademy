package eduar.studymindredacao.config;

import eduar.studymindredacao.application.usecase.LimitadorConcorrenciaIA;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.EnumMap;
import java.util.Map;

/**
 * Máximo de chamadas simultâneas à IA por recurso (app.ia.concorrencia.*).
 * Valor zero ou negativo derruba a subida com o nome da variável, em vez de travar o recurso em produção.
 */
@Configuration
public class ConcorrenciaIAConfig {

    @Bean
    LimitadorConcorrenciaIA limitadorConcorrenciaIA(
            @Value("${app.ia.concorrencia.correcao}") int correcao,
            @Value("${app.ia.concorrencia.transcricao}") int transcricao,
            @Value("${app.ia.concorrencia.julia}") int julia
    ) {
        Map<RecursoIA, Integer> maximos = new EnumMap<>(RecursoIA.class);
        maximos.put(RecursoIA.CORRECAO, positivo(correcao, "IA_CONCORRENCIA_CORRECAO"));
        maximos.put(RecursoIA.TRANSCRICAO, positivo(transcricao, "IA_CONCORRENCIA_TRANSCRICAO"));
        maximos.put(RecursoIA.JULIA, positivo(julia, "IA_CONCORRENCIA_JULIA"));
        return new LimitadorConcorrenciaIA(maximos);
    }

    private static int positivo(int valor, String variavel) {
        if (valor <= 0) {
            throw new IllegalStateException(variavel + " deve ser maior que zero");
        }
        return valor;
    }
}
