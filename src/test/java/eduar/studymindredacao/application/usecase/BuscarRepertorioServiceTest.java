package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.exception.RepertorioNaoEncontradoException;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.model.enums.TipoEntidade;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static eduar.studymindredacao.application.usecase.Repertorios.SAUDE_MENTAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuscarRepertorioServiceTest {
    private final RepertorioRepositoryEmMemoria repertorios = new RepertorioRepositoryEmMemoria();
    private final BuscarRepertorioService service = new BuscarRepertorioService(repertorios, PlanosDeTeste.pro());
    private final BuscarRepertorioService doGratis = new BuscarRepertorioService(repertorios, PlanosDeTeste.gratis());
    private final UUID aluno = UUID.randomUUID();

    @Test
    void encontraAtivoPeloCodigo() {
        repertorios.salvar(Repertorios.ativo("REP-025", "Rita Barradas Barata", TipoEntidade.PERSON));

        assertThat(service.buscar(aluno, "REP-025").nome()).isEqualTo("Rita Barradas Barata");
    }

    @Test
    void aceitaCodigoEmMinusculasEComEspacos() {
        repertorios.salvar(Repertorios.ativo("REP-025", "Rita Barradas Barata", TipoEntidade.PERSON));

        assertThat(service.buscar(aluno, " rep-025 ").codigo()).isEqualTo("REP-025");
    }

    @Test
    void inativoRespondeComoInexistente() {
        repertorios.salvar(Repertorios.inativo("REP-001", "Inativo"));

        assertThatThrownBy(() -> service.buscar(aluno, "REP-001"))
                .isInstanceOf(RepertorioNaoEncontradoException.class)
                .hasMessageContaining("REP-001");
    }

    @Test
    void codigoInexistente() {
        assertThatThrownBy(() -> service.buscar(aluno, "REP-999"))
                .isInstanceOf(RepertorioNaoEncontradoException.class);
    }

    // ---------- plano ----------

    @Test
    void gratisAbreOLiberadoDoProblema() {
        repertorios.salvar(Repertorios.ativo("REP-030", "Bauman", TipoEntidade.PERSON, SAUDE_MENTAL));
        repertorios.salvar(Repertorios.ativo("REP-031", "Durkheim", TipoEntidade.PERSON, SAUDE_MENTAL));

        assertThat(doGratis.buscar(aluno, "rep-030").codigo()).isEqualTo("REP-030");
    }

    @Test
    void gratisRecebe403NoNaoLiberado() {
        repertorios.salvar(Repertorios.ativo("REP-030", "Bauman", TipoEntidade.PERSON, SAUDE_MENTAL));
        repertorios.salvar(Repertorios.ativo("REP-031", "Durkheim", TipoEntidade.PERSON, SAUDE_MENTAL));

        assertThatThrownBy(() -> doGratis.buscar(aluno, "REP-031"))
                .isInstanceOfSatisfying(RecursoExclusivoProException.class,
                        e -> assertThat(e.getRecurso()).isEqualTo(RecursoPro.REPERTORIO_COMPLETO));
    }

    @Test
    void inexistenteDa404AntesDo403NoGratis() {
        assertThatThrownBy(() -> doGratis.buscar(aluno, "REP-999"))
                .isInstanceOf(RepertorioNaoEncontradoException.class);
    }

    @Test
    void proAbreQualquerUm() {
        repertorios.salvar(Repertorios.ativo("REP-030", "Bauman", TipoEntidade.PERSON, SAUDE_MENTAL));
        repertorios.salvar(Repertorios.ativo("REP-031", "Durkheim", TipoEntidade.PERSON, SAUDE_MENTAL));

        assertThat(service.buscar(aluno, "REP-031").codigo()).isEqualTo("REP-031");
    }
}
