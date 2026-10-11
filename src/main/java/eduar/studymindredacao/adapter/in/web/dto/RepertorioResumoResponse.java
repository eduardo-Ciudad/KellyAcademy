package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.model.enums.Nivel;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;

import java.util.List;

/** Card da listagem: o suficiente para o aluno decidir se abre o detalhe. */
public record RepertorioResumoResponse(
        String codigo,
        String nome,
        String subtitulo,
        TipoEntidade tipo,
        String tipoDescricao,
        String lembreNaProva,
        Nivel saturacao,
        Nivel riscoUso,
        List<ProblemaSocialResponse> problemas
) {
    public static RepertorioResumoResponse de(Repertorio repertorio) {
        return new RepertorioResumoResponse(
                repertorio.codigo(),
                repertorio.nome(),
                repertorio.subtitulo(),
                repertorio.tipoEntidade(),
                repertorio.tipoDescricao(),
                repertorio.conteudo().lembreNaProva(),
                repertorio.curadoria().saturacao(),
                repertorio.curadoria().riscoUso(),
                repertorio.problemas().stream().map(ProblemaSocialResponse::de).toList()
        );
    }
}
