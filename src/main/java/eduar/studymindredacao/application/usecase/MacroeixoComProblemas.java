package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Macroeixo;
import eduar.studymindredacao.domain.model.ProblemaSocial;

import java.util.List;

/** Modelo de leitura dos filtros do Repertório: um macroeixo e os problemas sociais dele, em ordem. */
public record MacroeixoComProblemas(Macroeixo macroeixo, List<ProblemaSocial> problemas) {
    public MacroeixoComProblemas {
        problemas = List.copyOf(problemas);
    }
}
