package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.model.ConteudoDidatico;
import eduar.studymindredacao.domain.model.Curadoria;
import eduar.studymindredacao.domain.model.DadosEvidencia;
import eduar.studymindredacao.domain.model.Fonte;
import eduar.studymindredacao.domain.model.Pagina;
import eduar.studymindredacao.domain.model.ProblemaSocial;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.model.enums.Dificuldade;
import eduar.studymindredacao.domain.model.enums.FuncaoArgumentativa;
import eduar.studymindredacao.domain.model.enums.Nivel;
import eduar.studymindredacao.domain.model.enums.PapelRepertorio;
import eduar.studymindredacao.domain.model.enums.StatusVerificacao;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import eduar.studymindredacao.domain.model.enums.TipoEvidencia;
import eduar.studymindredacao.domain.model.enums.TipoFonte;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RepertorioResponseTest {
    private static final ProblemaSocial SAUDE_MENTAL =
            new ProblemaSocial(null, "saude-mental", "Saúde mental", "saude");
    private static final Fonte FONTE = new Fonte(TipoFonte.GENERAL, "IBGE", "https://ibge.gov.br");

    private static Repertorio repertorio(String codigo, TipoEntidade tipo, DadosEvidencia evidencia) {
        return new Repertorio(
                null, codigo, tipo, PapelRepertorio.BOTH, "Nome", "Subtítulo", "PESQUISA", "Área", "Brasil",
                new ConteudoDidatico("Ideia", "Lembre", "Como usar", "Exemplo", "Erro"),
                new Curadoria(Nivel.MEDIUM, Dificuldade.BASIC, Nivel.LOW, null), evidencia,
                List.of(FuncaoArgumentativa.PROVE_PROBLEM), List.of(), List.of("tag"),
                List.of(SAUDE_MENTAL), List.of(FONTE), StatusVerificacao.VERIFIED, null, true
        );
    }

    @Test
    void resumoLevaOsSelosEOsProblemas() {
        var resumo = RepertorioResumoResponse.de(repertorio("REP-025", TipoEntidade.PERSON, null));

        assertThat(resumo.codigo()).isEqualTo("REP-025");
        assertThat(resumo.lembreNaProva()).isEqualTo("Lembre");
        assertThat(resumo.saturacao()).isEqualTo(Nivel.LOW);
        assertThat(resumo.riscoUso()).isEqualTo(Nivel.MEDIUM);
        assertThat(resumo.problemas()).containsExactly(
                new ProblemaSocialResponse("saude-mental", "Saúde mental", "saude"));
    }

    @Test
    void detalheDePessoaVemSemBlocoDeEvidencia() {
        var detalhe = RepertorioDetalheResponse.de(repertorio("REP-025", TipoEntidade.PERSON, null));

        assertThat(detalhe.evidencia()).isNull();
        assertThat(detalhe.comoUsar()).isEqualTo("Como usar");
        assertThat(detalhe.fontes()).containsExactly(
                new RepertorioDetalheResponse.FonteResponse(TipoFonte.GENERAL, "IBGE", "https://ibge.gov.br"));
    }

    @Test
    void detalheDeEvidenciaTrazTiposPopulacaoEAno() {
        var dados = new DadosEvidencia(List.of(TipoEvidencia.NATIONAL_SURVEY), "Adolescentes", 2019);

        var detalhe = RepertorioDetalheResponse.de(repertorio("EVD-001", TipoEntidade.EVIDENCE, dados));

        assertThat(detalhe.evidencia()).isEqualTo(new RepertorioDetalheResponse.EvidenciaResponse(
                List.of(TipoEvidencia.NATIONAL_SURVEY), "Adolescentes", 2019));
    }

    @Test
    void paginaConverteOsItensEMantemOsTotais() {
        var pagina = new Pagina<>(List.of(repertorio("REP-025", TipoEntidade.PERSON, null)), 0, 1, 3);

        var resposta = PaginaResponse.de(pagina, RepertorioResumoResponse::de);

        assertThat(resposta.itens()).extracting(RepertorioResumoResponse::codigo).containsExactly("REP-025");
        assertThat(resposta.totalItens()).isEqualTo(3);
        assertThat(resposta.totalPaginas()).isEqualTo(3);
        assertThat(resposta.temProxima()).isTrue();
    }
}
