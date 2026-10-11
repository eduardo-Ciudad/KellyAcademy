package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.application.usecase.MacroeixoComProblemas;

import java.util.List;

/** Um item do filtro em dois níveis: o macroeixo e seus problemas sociais. A ordem vem pronta do backend. */
public record MacroeixoResponse(String slug, String nome, List<ProblemaSocialResponse> problemas) {
    public static MacroeixoResponse de(MacroeixoComProblemas item) {
        return new MacroeixoResponse(
                item.macroeixo().slug(),
                item.macroeixo().nome(),
                item.problemas().stream().map(ProblemaSocialResponse::de).toList()
        );
    }
}
