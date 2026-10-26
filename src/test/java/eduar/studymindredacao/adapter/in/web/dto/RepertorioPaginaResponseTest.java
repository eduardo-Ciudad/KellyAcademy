package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.application.usecase.ListagemRepertorios;
import eduar.studymindredacao.domain.model.ConteudoDidatico;
import eduar.studymindredacao.domain.model.Fonte;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.ProblemaSocial;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.model.enums.FuncaoArgumentativa;
import eduar.studymindredacao.domain.model.enums.PapelRepertorio;
import eduar.studymindredacao.domain.model.enums.StatusVerificacao;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import eduar.studymindredacao.domain.model.enums.TipoFonte;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepertorioPaginaResponseTest {

    private static Repertorio repertorio(String codigo) {
        return new Repertorio(
                null, codigo, TipoEntidade.PERSON, PapelRepertorio.REPERTOIRE, "Nome", null, null, null, null,
                new ConteudoDidatico("Ideia", "Lembre", "Como usar", "Exemplo", "Erro"),
                null, null, List.of(FuncaoArgumentativa.EXPLAIN_CAUSE), List.of(), List.of(),
                List.of(new ProblemaSocial(null, "saude-mental", "Saúde mental", "saude")),
                List.of(new Fonte(TipoFonte.GENERAL, "Fonte", "https://exemplo.org")),
                StatusVerificacao.VERIFIED, null, true
        );
    }

    @Test
    void levaOsCamposDaPaginaEAFlagDoPlano() {
        var pagina = new Pagina<>(List.of(repertorio("REP-030")), 0, 20, 21);

        var resposta = RepertorioPaginaResponse.de(new ListagemRepertorios(pagina, true));

        assertThat(resposta.itens()).extracting(RepertorioResumoResponse::codigo).containsExactly("REP-030");
        assertThat(resposta.pagina()).isZero();
        assertThat(resposta.tamanho()).isEqualTo(20);
        assertThat(resposta.totalItens()).isEqualTo(21);
        assertThat(resposta.totalPaginas()).isEqualTo(2);
        assertThat(resposta.temProxima()).isTrue();
        assertThat(resposta.limitadoAoPlano()).isTrue();
    }

    @Test
    void proVemSemLimite() {
        var resposta = RepertorioPaginaResponse.de(new ListagemRepertorios(new Pagina<>(List.of(), 0, 20, 0), false));

        assertThat(resposta.limitadoAoPlano()).isFalse();
    }

    @Test
    void mantemOsCamposDoPaginaResponseEAcrescentaSoAFlag() {
        var deHoje = PaginaResponse.class.getRecordComponents();
        var novo = RepertorioPaginaResponse.class.getRecordComponents();

        assertThat(novo).hasSize(deHoje.length + 1);
        for (int i = 0; i < deHoje.length; i++) {
            assertThat(novo[i].getName()).isEqualTo(deHoje[i].getName());
        }
        assertThat(novo[deHoje.length].getName()).isEqualTo("limitadoAoPlano");
    }
}
