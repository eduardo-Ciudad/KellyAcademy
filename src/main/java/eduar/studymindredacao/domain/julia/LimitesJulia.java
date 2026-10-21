package eduar.studymindredacao.domain.julia;

import java.time.Duration;
import java.util.Objects;

public record LimitesJulia(
        int limitePorConversa,
        int tamanhoMaxPergunta,
        int historicoMaximo,
        Duration inatividadeMaxima
) {
    public LimitesJulia {
        if (limitePorConversa <= 0) {
            throw new IllegalArgumentException("limitePorConversa deve ser positivo");
        }
        if (tamanhoMaxPergunta <= 0) {
            throw new IllegalArgumentException("tamanhoMaxPergunta deve ser positivo");
        }
        if (historicoMaximo <= 0 || historicoMaximo % 2 != 0) {
            throw new IllegalArgumentException("historicoMaximo deve ser positivo e par");
        }
        Objects.requireNonNull(inatividadeMaxima, "inatividadeMaxima é obrigatória");
        if (inatividadeMaxima.isZero() || inatividadeMaxima.isNegative()) {
            throw new IllegalArgumentException("inatividadeMaxima deve ser positiva");
        }
    }
}