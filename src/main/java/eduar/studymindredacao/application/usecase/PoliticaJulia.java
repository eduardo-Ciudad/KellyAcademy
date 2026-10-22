package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.LimitesJulia;

import java.time.Duration;
import java.util.Objects;


public record PoliticaJulia(LimitesJulia limites, int limiteDiario, int tetoGlobal) {

    public PoliticaJulia {
        Objects.requireNonNull(limites, "limites é obrigatório");
        if (limiteDiario < 0) {
            throw new IllegalArgumentException("o limite diário da Júlia não pode ser negativo");
        }
        if (tetoGlobal < 0) {
            throw new IllegalArgumentException("o teto global da Júlia não pode ser negativo");
        }
    }

    public static PoliticaJulia de(int porConversa, int tamanhoPergunta, int historico, int inatividadeHoras,
                                   int limiteDiario, int tetoGlobal) {
        var limites = new LimitesJulia(porConversa, tamanhoPergunta, historico, Duration.ofHours(inatividadeHoras));
        return new PoliticaJulia(limites, limiteDiario, tetoGlobal);
    }

    public int restantesHoje(int usadasHoje) {
        return Math.max(0, limiteDiario - usadasHoje);
    }
}
