package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.FiltroRepertorio;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static eduar.studymindredacao.application.usecase.Repertorios.EVASAO_ESCOLAR;
import static eduar.studymindredacao.application.usecase.Repertorios.SAUDE_MENTAL;
import static org.assertj.core.api.Assertions.assertThat;

class ListarRepertoriosServiceTest {
    private final RepertorioRepositoryEmMemoria repertorios = new RepertorioRepositoryEmMemoria();
    private final ListarRepertoriosService service = new ListarRepertoriosService(repertorios, PlanosDeTeste.pro());
    private final ListarRepertoriosService doGratis = new ListarRepertoriosService(repertorios, PlanosDeTeste.gratis());
    private final UUID aluno = UUID.randomUUID();

    @Test
    void repassaOFiltroEDevolveAPaginaDaPorta() {
        repertorios.salvar(Repertorios.ativo("REP-025", "Zygmunt", TipoEntidade.PERSON));
        repertorios.salvar(Repertorios.ativo("REP-026", "Anomia", TipoEntidade.CONCEPT));
        var filtro = new FiltroRepertorio(null, null, TipoEntidade.PERSON, null, null, 0, 20);

        var listagem = service.listar(aluno, filtro);

        assertThat(repertorios.ultimoFiltro()).isSameAs(filtro);
        assertThat(listagem.pagina().itens()).extracting(r -> r.codigo()).containsExactly("REP-025");
        assertThat(listagem.pagina().totalItens()).isEqualTo(1);
    }

    @Test
    void naoListaInativos() {
        repertorios.salvar(Repertorios.ativo("REP-025", "Ativo", TipoEntidade.PERSON));
        repertorios.salvar(Repertorios.inativo("REP-001", "Inativo"));

        var listagem = service.listar(aluno, FiltroRepertorio.semFiltros(0));

        assertThat(listagem.pagina().itens()).extracting(r -> r.codigo()).containsExactly("REP-025");
    }

    // ---------- plano ----------

    private void salvarDoisPorProblema() {
        repertorios.salvar(Repertorios.ativo("REP-030", "Bauman", TipoEntidade.PERSON, SAUDE_MENTAL));
        repertorios.salvar(Repertorios.ativo("REP-031", "Durkheim", TipoEntidade.PERSON, SAUDE_MENTAL));
        repertorios.salvar(Repertorios.ativo("CF-002", "Art. 6º", TipoEntidade.LEGAL_SOURCE, EVASAO_ESCOLAR));
        repertorios.salvar(Repertorios.ativo("REP-040", "Paulo Freire", TipoEntidade.PERSON, EVASAO_ESCOLAR));
    }

    @Test
    void proVeTudoSemLimite() {
        salvarDoisPorProblema();

        var listagem = service.listar(aluno, FiltroRepertorio.semFiltros(0));

        assertThat(listagem.limitadoAoPlano()).isFalse();
        assertThat(repertorios.ultimoSomenteGratis()).isFalse();
        assertThat(listagem.pagina().totalItens()).isEqualTo(4);
    }

    @Test
    void gratisVeUmPorProblemaComREPPrimeiroEAFlagLigada() {
        salvarDoisPorProblema();

        var listagem = doGratis.listar(aluno, FiltroRepertorio.semFiltros(0));

        assertThat(listagem.limitadoAoPlano()).isTrue();
        assertThat(repertorios.ultimoSomenteGratis()).isTrue();
        // saude-mental: REP-030 (menor REP); evasao-escolar: REP-040 (REP antes do CF-002)
        assertThat(listagem.pagina().itens()).extracting(r -> r.codigo())
                .containsExactlyInAnyOrder("REP-030", "REP-040");
    }

    @Test
    void filtrosContinuamValendoNoGratis() {
        salvarDoisPorProblema();
        var porProblema = new FiltroRepertorio(null, "saude-mental", null, null, null, 0, 20);

        var listagem = doGratis.listar(aluno, porProblema);

        assertThat(listagem.pagina().itens()).extracting(r -> r.codigo()).containsExactly("REP-030");
    }
}
