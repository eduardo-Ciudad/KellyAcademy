package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.ProblemaSocial;
import eduar.studymindredacao.domain.model.Repertorio;
import eduar.studymindredacao.domain.model.enums.FuncaoArgumentativa;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import eduar.studymindredacao.domain.port.RepertorioRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integração com o Postgres real (Flyway aplica V1–V14). Slice de JPA: sobe só o banco e este adapter,
 * sem JWT, Gemini e web, então só precisa das variáveis do banco (DB_URL, DB_USERNAME, DB_PASSWORD).
 * Usa só o seed: nada é inserido. Os asserts evitam depender de textos que mudam com ajustes de conteúdo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(RepertorioRepositoryAdapter.class)
class RepertorioRepositoryAdapterIntegrationTest {
    /** Ativos no seed atual (até V22). Atualizar quando entrar novo lote de repertório. */
    private static final int ATIVOS_NO_SEED = 108;
    /** LEGAL_SOURCE ativos no seed atual (CF + leis dos REP). */
    private static final int LEIS_NO_SEED = 37;
    private static final int TOTAL_PAGINAS =
            (ATIVOS_NO_SEED + FiltroRepertorio.TAMANHO_PADRAO - 1) / FiltroRepertorio.TAMANHO_PADRAO;

    @Autowired
    private RepertorioRepositoryPort repertorios;

    @Test
    void semFiltroTrazTodosOsAtivosEmOrdemAlfabetica() {
        var primeira = repertorios.buscar(FiltroRepertorio.semFiltros(0));

        assertThat(primeira.totalItens()).isEqualTo(ATIVOS_NO_SEED);
        assertThat(primeira.itens()).hasSize(FiltroRepertorio.TAMANHO_PADRAO);
        assertThat(primeira.totalPaginas()).isEqualTo(TOTAL_PAGINAS);
        assertThat(primeira.itens()).allMatch(Repertorio::ativo);
        // Só a 1ª letra: o desempate fino (espaço, pontuação, "º") segue a collation do Postgres, não a do Java
        assertThat(primeira.itens().stream().map(r -> semAcento(r.nome()).substring(0, 1)).toList()).isSorted();
    }

    @Test
    void paginasNaoRepetemItens() {
        var primeira = repertorios.buscar(FiltroRepertorio.semFiltros(0));
        var segunda = repertorios.buscar(FiltroRepertorio.semFiltros(1));
        var ultima = repertorios.buscar(FiltroRepertorio.semFiltros(TOTAL_PAGINAS - 1));

        assertThat(codigos(segunda.itens())).doesNotContainAnyElementsOf(codigos(primeira.itens()));
        assertThat(ultima.itens()).hasSize(ATIVOS_NO_SEED - (TOTAL_PAGINAS - 1) * FiltroRepertorio.TAMANHO_PADRAO);
        assertThat(ultima.temProxima()).isFalse();
    }

    @Test
    void paginaAlemDoFimVemVaziaComTotal() {
        var pagina = repertorios.buscar(FiltroRepertorio.semFiltros(TOTAL_PAGINAS + 5));

        assertThat(pagina.itens()).isEmpty();
        assertThat(pagina.totalItens()).isEqualTo(ATIVOS_NO_SEED);
    }

    @Test
    void filtraPorTipo() {
        var pagina = repertorios.buscar(filtro(null, null, TipoEntidade.LEGAL_SOURCE, null, null));

        assertThat(pagina.totalItens()).isEqualTo(LEIS_NO_SEED);
        assertThat(pagina.itens()).allMatch(r -> r.tipoEntidade() == TipoEntidade.LEGAL_SOURCE);
    }

    @Test
    void filtraPorFuncaoArgumentativa() {
        var pagina = repertorios.buscar(filtro(null, null, null, FuncaoArgumentativa.EXPLAIN_CAUSE, null));

        assertThat(pagina.totalItens()).isPositive().isLessThan(ATIVOS_NO_SEED);
        assertThat(pagina.itens()).allMatch(r -> r.funcoesArgumentativas().contains(FuncaoArgumentativa.EXPLAIN_CAUSE));
    }

    @Test
    void filtraPorMacroeixo() {
        var pagina = repertorios.buscar(filtro("saude", null, null, null, null));

        assertThat(pagina.totalItens()).isPositive().isLessThan(ATIVOS_NO_SEED);
        assertThat(pagina.itens()).allMatch(r -> r.problemas().stream().map(ProblemaSocial::macroeixoSlug).anyMatch("saude"::equals));
    }

    @Test
    void filtraPorProblemaSocial() {
        var pagina = repertorios.buscar(filtro(null, "saude-mental", null, null, null));

        assertThat(pagina.totalItens()).isPositive();
        assertThat(pagina.itens()).allMatch(r -> r.problemas().stream().map(ProblemaSocial::slug).anyMatch("saude-mental"::equals));
    }

    @Test
    void combinaFiltros() {
        var soMacroeixo = repertorios.buscar(filtro("saude", null, null, null, null));
        var combinado = repertorios.buscar(filtro("saude", null, TipoEntidade.PERSON, null, null));

        assertThat(combinado.totalItens()).isPositive().isLessThanOrEqualTo(soMacroeixo.totalItens());
        assertThat(combinado.itens()).allMatch(r -> r.tipoEntidade() == TipoEntidade.PERSON);
    }

    @Test
    void buscaIgnoraAcentoEMaiusculas() {
        var semAcento = repertorios.buscar(filtro(null, null, null, null, "CONSTITUICAO"));
        var comAcento = repertorios.buscar(filtro(null, null, null, null, "constituição"));

        assertThat(semAcento.totalItens()).isGreaterThanOrEqualTo(12);
        assertThat(codigos(semAcento.itens())).isEqualTo(codigos(comAcento.itens()));
    }

    @Test
    void buscaTrataPorcentagemComoTextoLiteral() {
        var pagina = repertorios.buscar(filtro(null, null, null, null, "%"));

        // Sem o escape, "%" casaria com todos os ativos; com ele, só os que têm "%" no texto (dados das EVD)
        assertThat(pagina.totalItens()).isPositive().isLessThan(ATIVOS_NO_SEED);
        assertThat(pagina.itens()).allMatch(r -> r.nome().contains("%")
                || (r.conteudo().ideiaCentral() != null && r.conteudo().ideiaCentral().contains("%"))
                || r.tags().stream().anyMatch(t -> t.contains("%")));
    }

    @Test
    void buscaSemResultado() {
        var pagina = repertorios.buscar(filtro(null, null, null, null, "xyzinexistente"));

        assertThat(pagina.itens()).isEmpty();
        assertThat(pagina.totalItens()).isZero();
    }

    @Test
    void buscaPorCodigoTrazFontesEProblemas() {
        var cf = repertorios.buscarAtivoPorCodigo("CF-006");

        assertThat(cf).isPresent();
        assertThat(cf.get().fontes()).isNotEmpty();
        assertThat(cf.get().problemas()).isNotEmpty();
    }

    @Test
    void loteRep001a024EstaAtivo() {
        assertThat(repertorios.buscarAtivoPorCodigo("REP-001")).isPresent();
        assertThat(repertorios.buscarAtivoPorCodigo("REP-024")).isPresent();
        // REP-007, 015 e 020 entram inativos na V20 (fonte direta pendente): não podem aparecer
        assertThat(repertorios.buscarAtivoPorCodigo("REP-007")).isEmpty();
    }

    @Test
    void codigoForaDaBaseVemVazio() {
        assertThat(repertorios.buscarAtivoPorCodigo("REP-999")).isEmpty();
    }

    private static FiltroRepertorio filtro(String macroeixo, String problema, TipoEntidade tipo,
                                           FuncaoArgumentativa funcao, String busca) {
        return new FiltroRepertorio(macroeixo, problema, tipo, funcao, busca, 0, FiltroRepertorio.TAMANHO_MAXIMO);
    }

    private static List<String> codigos(List<Repertorio> itens) {
        return itens.stream().map(Repertorio::codigo).toList();
    }

    private static String semAcento(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
