package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.ConteudoDidatico;
import eduar.studymindredacao.domain.model.Fonte;
import eduar.studymindredacao.domain.model.ProblemaSocial;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.model.enums.FuncaoArgumentativa;
import eduar.studymindredacao.domain.model.enums.PapelRepertorio;
import eduar.studymindredacao.domain.model.enums.StatusVerificacao;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import eduar.studymindredacao.domain.model.enums.TipoFonte;

import java.util.List;

/** Fábrica de repertórios válidos para os testes de caso de uso. */
final class Repertorios {
    static final ProblemaSocial SAUDE_MENTAL = new ProblemaSocial(null, "saude-mental", "Saúde mental", "saude");
    static final ProblemaSocial EVASAO_ESCOLAR =
            new ProblemaSocial(null, "evasao-escolar", "Evasão escolar", "educacao");

    private Repertorios() {
    }

    static Repertorio ativo(String codigo, String nome, TipoEntidade tipo) {
        return ativo(codigo, nome, tipo, SAUDE_MENTAL);
    }

    static Repertorio ativo(String codigo, String nome, TipoEntidade tipo, ProblemaSocial... problemas) {
        return new Repertorio(
                null, codigo, tipo, PapelRepertorio.REPERTOIRE, nome, null, null, null, null,
                new ConteudoDidatico("Ideia central", "Lembre na prova", "Como usar", "Exemplo", "Erro comum"),
                null, null, List.of(FuncaoArgumentativa.EXPLAIN_CAUSE), List.of(), List.of(),
                List.of(problemas), List.of(new Fonte(TipoFonte.GENERAL, "Fonte", "https://exemplo.org")),
                StatusVerificacao.VERIFIED, null, true
        );
    }

    static Repertorio inativo(String codigo, String nome) {
        return new Repertorio(
                null, codigo, TipoEntidade.PERSON, PapelRepertorio.REPERTOIRE, nome, null, null, null, null,
                null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                StatusVerificacao.PARTIAL, null, false
        );
    }
}
