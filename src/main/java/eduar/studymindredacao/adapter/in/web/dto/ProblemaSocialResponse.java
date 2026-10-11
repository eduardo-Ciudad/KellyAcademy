package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.model.ProblemaSocial;

/** Problema social como o front usa: o slug serve de valor do filtro, o nome de rótulo. */
public record ProblemaSocialResponse(String slug, String nome, String macroeixo) {
    public static ProblemaSocialResponse de(ProblemaSocial problema) {
        return new ProblemaSocialResponse(problema.slug(), problema.nome(), problema.macroeixoSlug());
    }
}
