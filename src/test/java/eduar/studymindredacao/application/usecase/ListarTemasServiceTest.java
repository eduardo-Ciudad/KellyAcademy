package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ListarTemasServiceTest {
    private final TemaRepositoryEmMemoria temas = new TemaRepositoryEmMemoria();
    private final UsuarioTemaRepositoryEmMemoria usuarioTemas = new UsuarioTemaRepositoryEmMemoria();
    private final ListarTemasService service = new ListarTemasService(temas, usuarioTemas);

    @Test
    void listaSoTemasAtivos() {
        temas.salvar(new Tema(null, "Ativo", null, OrigemTema.ENEM_OFICIAL, (short) 2024, true, null));
        temas.salvar(new Tema(null, "Desativado", null, OrigemTema.AUTORAL, null, false, null));

        var ativos = service.listarAtivos();

        assertThat(ativos).extracting(Tema::titulo).containsExactly("Ativo");
    }

    @Test
    void disponiveisIncluemSoOsTemasPossiveisQueOAlunoAdicionou() {
        UUID aluno = UUID.randomUUID();
        UUID outroAluno = UUID.randomUUID();
        temas.salvar(new Tema(null, "Oficial", null, OrigemTema.ENEM_OFICIAL, (short) 2024, true, null));
        var adicionado = temas.salvar(new Tema(null, "Possível adicionado", null, OrigemTema.PREVISAO, (short) 2026, true, null));
        var naoAdicionado = temas.salvar(new Tema(null, "Possível não adicionado", null, OrigemTema.PREVISAO, (short) 2026, true, null));
        usuarioTemas.adicionar(aluno, adicionado.id());
        usuarioTemas.adicionar(outroAluno, naoAdicionado.id());

        var disponiveis = service.listarDisponiveis(aluno);

        assertThat(disponiveis).extracting(Tema::titulo)
                .containsExactlyInAnyOrder("Oficial", "Possível adicionado");
    }

    @Test
    void temaPossivelDesativadoSomeMesmoSeAdicionado() {
        UUID aluno = UUID.randomUUID();
        var desativado = temas.salvar(new Tema(null, "Possível desativado", null, OrigemTema.PREVISAO, (short) 2026, false, null));
        usuarioTemas.adicionar(aluno, desativado.id());

        assertThat(service.listarDisponiveis(aluno)).isEmpty();
    }

    @Test
    void disponiveisTrazemOsPersonalizadosDoAlunoPrimeiroESoOsDele() {
        UUID aluno = UUID.randomUUID();
        UUID outroAluno = UUID.randomUUID();
        temas.salvar(new Tema(null, "Oficial", null, OrigemTema.ENEM_OFICIAL, (short) 2024, true, null));
        var meu = temas.salvar(Tema.personalizado("Meu tema digitado aqui", aluno));
        temas.salvar(Tema.personalizado("Tema digitado pelo outro", outroAluno));

        var disponiveis = service.listarDisponiveis(aluno);

        assertThat(disponiveis).extracting(Tema::titulo).containsExactly(meu.titulo(), "Oficial");
    }

    @Test
    void listarAtivosNaoTrazPersonalizados() {
        temas.salvar(new Tema(null, "Oficial", null, OrigemTema.ENEM_OFICIAL, (short) 2024, true, null));
        temas.salvar(Tema.personalizado("Meu tema digitado aqui", UUID.randomUUID()));

        assertThat(service.listarAtivos()).extracting(Tema::titulo).containsExactly("Oficial");
    }
}
