package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.model.DadosEvidencia;
import eduar.studymindredacao.domain.model.Fonte;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.model.enums.Dificuldade;
import eduar.studymindredacao.domain.model.enums.FuncaoArgumentativa;
import eduar.studymindredacao.domain.model.enums.Nivel;
import eduar.studymindredacao.domain.model.enums.PapelRepertorio;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import eduar.studymindredacao.domain.model.enums.TipoEvidencia;
import eduar.studymindredacao.domain.model.enums.TipoFonte;

import java.util.List;

/** Detalhe completo para o aluno. Notas da rubrica, tags e dados de verificação ficam de fora de propósito. */
public record RepertorioDetalheResponse(
        String codigo,
        TipoEntidade tipo,
        PapelRepertorio papel,
        String nome,
        String subtitulo,
        String tipoDescricao,
        String area,
        String pais,
        String ideiaCentral,
        String lembreNaProva,
        String comoUsar,
        String exemploAplicacao,
        String erroComum,
        Nivel riscoUso,
        Dificuldade dificuldade,
        Nivel saturacao,
        List<FuncaoArgumentativa> funcoesArgumentativas,
        List<ProblemaSocialResponse> problemas,
        List<FonteResponse> fontes,
        EvidenciaResponse evidencia
) {
    public record FonteResponse(TipoFonte tipo, String descricao, String url) {
        static FonteResponse de(Fonte fonte) {
            return new FonteResponse(fonte.tipo(), fonte.descricao(), fonte.url());
        }
    }

    /** Só existe quando o registro traz dados de evidência; o ano permite ao front avisar que o dado é antigo. */
    public record EvidenciaResponse(List<TipoEvidencia> tipos, String populacao, Integer ano) {
        static EvidenciaResponse de(DadosEvidencia evidencia) {
            boolean vazia = !evidencia.temTipo() && evidencia.populacao() == null && evidencia.ano() == null;
            return vazia ? null : new EvidenciaResponse(evidencia.tipos(), evidencia.populacao(), evidencia.ano());
        }
    }

    public static RepertorioDetalheResponse de(Repertorio repertorio) {
        var conteudo = repertorio.conteudo();
        var curadoria = repertorio.curadoria();
        return new RepertorioDetalheResponse(
                repertorio.codigo(),
                repertorio.tipoEntidade(),
                repertorio.papel(),
                repertorio.nome(),
                repertorio.subtitulo(),
                repertorio.tipoDescricao(),
                repertorio.area(),
                repertorio.pais(),
                conteudo.ideiaCentral(),
                conteudo.lembreNaProva(),
                conteudo.comoUsar(),
                conteudo.exemploAplicacao(),
                conteudo.erroComum(),
                curadoria.riscoUso(),
                curadoria.dificuldade(),
                curadoria.saturacao(),
                repertorio.funcoesArgumentativas(),
                repertorio.problemas().stream().map(ProblemaSocialResponse::de).toList(),
                repertorio.fontes().stream().map(FonteResponse::de).toList(),
                EvidenciaResponse.de(repertorio.evidencia())
        );
    }
}
