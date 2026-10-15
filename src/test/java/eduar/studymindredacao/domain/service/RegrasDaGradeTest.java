package eduar.studymindredacao.domain.service;

import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.EvidenciaCompetencia;
import eduar.studymindredacao.domain.model.ResultadoAvaliacaoIA;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegrasDaGradeTest {

    private static final EvidenciaCompetencia SEM = EvidenciaCompetencia.NAO_INFORMADA;

    private static EvidenciaCompetencia c1(int falhas, int desvios) {
        return new EvidenciaCompetencia(falhas, desvios, null, null, null, null, null, null);
    }

    private static EvidenciaCompetencia c2(int produtivos) {
        return new EvidenciaCompetencia(null, null, produtivos, null, null, null, null, null);
    }

    private static EvidenciaCompetencia c3(int lacunas) {
        return new EvidenciaCompetencia(null, null, null, lacunas, null, null, null, null);
    }

    private static EvidenciaCompetencia c4(int repeticoes, int inadequacoes, Integer operadores) {
        return new EvidenciaCompetencia(null, null, null, null, repeticoes, inadequacoes, operadores, null);
    }

    private static EvidenciaCompetencia c5(int elementos) {
        return new EvidenciaCompetencia(null, null, null, null, null, null, null, elementos);
    }

    private static CompetenciaAvaliada comp(int numero, int nota, EvidenciaCompetencia evidencia, String... problemas) {
        return new CompetenciaAvaliada(numero, nota, "n" + numero, "r" + numero, List.of(problemas), evidencia);
    }

    /** Monta uma avaliação com 200 em tudo e substitui as competências informadas. */
    private static ResultadoAvaliacaoIA avaliacao(CompetenciaAvaliada... trocas) {
        List<CompetenciaAvaliada> competencias = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            competencias.add(comp(n, 200, SEM));
        }
        for (CompetenciaAvaliada troca : trocas) {
            competencias.set(troca.numero() - 1, troca);
        }
        return new ResultadoAvaliacaoIA(false, null, competencias, List.of(), List.of(), "d",
                "modelo", 10, 10, "{}");
    }

    private static int nota(RegrasDaGrade.Resultado resultado, int numero) {
        return resultado.avaliacao().competencias().get(numero - 1).nota();
    }

    @Test
    void semEvidenciasNaoMudaNada() {
        var original = avaliacao();
        var resultado = RegrasDaGrade.aplicar(original);

        assertThat(resultado.avaliacao()).isSameAs(original);
        assertThat(resultado.ajustes()).isEmpty();
    }

    @Test
    void c1ComUmaFalhaEDoisDesviosMantem200() {
        var resultado = RegrasDaGrade.aplicar(avaliacao(comp(1, 200, c1(1, 2), "falha", "d1", "d2")));

        assertThat(nota(resultado, 1)).isEqualTo(200);
    }

    @Test
    void c1ComDuasFalhasOuTresDesviosLimitaA160() {
        var falhas = RegrasDaGrade.aplicar(avaliacao(comp(1, 200, c1(2, 0), "f1", "f2")));
        var desvios = RegrasDaGrade.aplicar(avaliacao(comp(1, 200, c1(0, 3), "d1", "d2", "d3")));

        assertThat(nota(falhas, 1)).isEqualTo(160);
        assertThat(nota(desvios, 1)).isEqualTo(160);
    }

    @Test
    void c2SemRepertorioProdutivoLimitaA160() {
        var resultado = RegrasDaGrade.aplicar(avaliacao(comp(2, 200, c2(0))));

        assertThat(nota(resultado, 2)).isEqualTo(160);
        assertThat(RegrasDaGrade.aplicar(avaliacao(comp(2, 200, c2(1)))).ajustes()).isEmpty();
    }

    @Test
    void c3ComLacunaLimitaA160() {
        var resultado = RegrasDaGrade.aplicar(avaliacao(comp(3, 200, c3(1), "lacuna")));

        assertThat(nota(resultado, 3)).isEqualTo(160);
    }

    @Test
    void c4UmaRepeticaoMantem200EDuasLimitam() {
        var uma = RegrasDaGrade.aplicar(avaliacao(comp(4, 200, c4(1, 0, 3), "rep")));
        var duas = RegrasDaGrade.aplicar(avaliacao(comp(4, 200, c4(2, 0, 3), "rep1", "rep2")));

        assertThat(nota(uma, 4)).isEqualTo(200);
        assertThat(nota(duas, 4)).isEqualTo(160);
    }

    @Test
    void c4InadequacaoOuUmOperadorLimitaA160() {
        var inadequacao = RegrasDaGrade.aplicar(avaliacao(comp(4, 200, c4(0, 1, 3), "inad")));
        var umOperador = RegrasDaGrade.aplicar(avaliacao(comp(4, 200, c4(0, 0, 1))));

        assertThat(nota(inadequacao, 4)).isEqualTo(160);
        assertThat(nota(umOperador, 4)).isEqualTo(160);
    }

    @Test
    void c4SemOperadorInterparagrafoLimitaA120() {
        var resultado = RegrasDaGrade.aplicar(avaliacao(comp(4, 200, c4(0, 0, 0))));

        assertThat(nota(resultado, 4)).isEqualTo(120);
    }

    @Test
    void c5LimitaPelaContagemDeElementos() {
        assertThat(nota(RegrasDaGrade.aplicar(avaliacao(comp(5, 200, c5(4)))), 5)).isEqualTo(160);
        assertThat(nota(RegrasDaGrade.aplicar(avaliacao(comp(5, 200, c5(2)))), 5)).isEqualTo(80);
        assertThat(nota(RegrasDaGrade.aplicar(avaliacao(comp(5, 200, c5(0)))), 5)).isEqualTo(40);
    }

    @Test
    void nuncaAumentaANota() {
        var resultado = RegrasDaGrade.aplicar(avaliacao(comp(5, 120, c5(5), "detalhamento fraco")));

        assertThat(nota(resultado, 5)).isEqualTo(120);
        assertThat(resultado.ajustes()).isEmpty();
    }

    @Test
    void ajusteApareceComoProblemaParaOAluno() {
        var resultado = RegrasDaGrade.aplicar(avaliacao(comp(4, 200, c4(3, 0, 3), "r1", "r2", "r3")));

        assertThat(resultado.avaliacao().competencias().get(3).problemasIdentificados())
                .last().asString()
                .startsWith("Nota limitada a 160 pela grade do INEP")
                .contains("3");
        assertThat(resultado.ajustes()).singleElement()
                .satisfies(a -> {
                    assertThat(a.competencia()).isEqualTo(4);
                    assertThat(a.notaIA()).isEqualTo(200);
                    assertThat(a.notaFinal()).isEqualTo(160);
                });
    }

    @Test
    void notaAbaixoDe200SemProblemaViraIncoerenciaSemMudarNota() {
        var resultado = RegrasDaGrade.aplicar(avaliacao(comp(3, 160, SEM)));

        assertThat(nota(resultado, 3)).isEqualTo(160);
        assertThat(resultado.incoerencias()).containsExactly("C3: nota 160 sem problema listado");
    }

    @Test
    void redacaoAnuladaNaoEAjustada() {
        List<CompetenciaAvaliada> zeradas = new ArrayList<>();
        for (int n = 1; n <= 5; n++) {
            zeradas.add(comp(n, 0, c5(0)));
        }
        var anulada = new ResultadoAvaliacaoIA(true, "Fuga ao tema", zeradas, List.of(), List.of(), "d",
                "modelo", 1, 1, "{}");

        var resultado = RegrasDaGrade.aplicar(anulada);

        assertThat(resultado.avaliacao()).isSameAs(anulada);
        assertThat(resultado.incoerencias()).isEmpty();
    }

    /** Caso da issue #23: IA deu 200 em C2, C3 e C4 sem sustentar pela grade. */
    @Test
    void casoDaSuperdotacao() {
        var resultado = RegrasDaGrade.aplicar(avaliacao(
                comp(1, 160, c1(1, 1), "falta o que", "falta espaço"),
                comp(2, 200, c2(1)),
                comp(3, 200, c3(0)),
                comp(4, 200, c4(3, 0, 3), "cabe destacar", "adequado", "plenamente"),
                comp(5, 200, c5(5))));

        assertThat(resultado.avaliacao().notaTotal()).isEqualTo(920);
        assertThat(resultado.ajustes()).extracting(RegrasDaGrade.Ajuste::competencia).containsExactly(4);
    }
}
