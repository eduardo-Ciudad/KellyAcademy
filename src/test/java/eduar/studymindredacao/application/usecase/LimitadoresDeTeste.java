package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.enums.RecursoIA;

import java.util.EnumMap;
import java.util.Map;

/** Limitadores de concorrência para os testes dos services, com os mesmos padrões de produção (4 / 2 / 4). */
final class LimitadoresDeTeste {

    private LimitadoresDeTeste() {
    }

    static LimitadorConcorrenciaIA padrao() {
        return new LimitadorConcorrenciaIA(maximosPadrao());
    }

    /** Muda o máximo de um recurso; os outros ficam no padrão. */
    static LimitadorConcorrenciaIA com(RecursoIA recurso, int maximo) {
        Map<RecursoIA, Integer> maximos = maximosPadrao();
        maximos.put(recurso, maximo);
        return new LimitadorConcorrenciaIA(maximos);
    }

    private static Map<RecursoIA, Integer> maximosPadrao() {
        Map<RecursoIA, Integer> maximos = new EnumMap<>(RecursoIA.class);
        maximos.put(RecursoIA.CORRECAO, 4);
        maximos.put(RecursoIA.TRANSCRICAO, 2);
        maximos.put(RecursoIA.JULIA, 4);
        return maximos;
    }
}
