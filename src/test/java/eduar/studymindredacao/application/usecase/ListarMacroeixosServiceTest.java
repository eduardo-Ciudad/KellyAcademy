package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Macroeixo;
import eduar.studymindredacao.domain.model.ProblemaSocial;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ListarMacroeixosServiceTest {
    private final MacroeixoRepositoryEmMemoria taxonomia = new MacroeixoRepositoryEmMemoria();
    private final ListarMacroeixosService service = new ListarMacroeixosService(taxonomia);

    @Test
    void agrupaOsProblemasEmCadaMacroeixoNaOrdemDeExibicao() {
        taxonomia.salvar(new Macroeixo(null, "educacao", "Educação", 2));
        taxonomia.salvar(new Macroeixo(null, "saude", "Saúde", 1));
        taxonomia.salvar(new ProblemaSocial(null, "acesso-saude", "Acesso à saúde", "saude"));
        taxonomia.salvar(new ProblemaSocial(null, "evasao-escolar", "Evasão escolar", "educacao"));
        taxonomia.salvar(new ProblemaSocial(null, "saude-mental", "Saúde mental", "saude"));

        var lista = service.listar();

        assertThat(lista).extracting(m -> m.macroeixo().slug()).containsExactly("saude", "educacao");
        assertThat(lista.get(0).problemas()).extracting(ProblemaSocial::slug)
                .containsExactly("acesso-saude", "saude-mental");
        assertThat(lista.get(1).problemas()).extracting(ProblemaSocial::slug)
                .containsExactly("evasao-escolar");
    }

    @Test
    void macroeixoSemProblemasVemComListaVazia() {
        taxonomia.salvar(new Macroeixo(null, "saude", "Saúde", 1));

        assertThat(service.listar()).singleElement()
                .satisfies(m -> assertThat(m.problemas()).isEmpty());
    }
}
