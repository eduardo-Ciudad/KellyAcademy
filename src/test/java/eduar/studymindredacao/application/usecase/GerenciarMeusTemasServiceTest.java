package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.exception.TemaNaoAdicionavelException;
import eduar.studymindredacao.domain.exception.TemaNaoEncontradoException;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GerenciarMeusTemasServiceTest {
    private final TemaRepositoryEmMemoria temas = new TemaRepositoryEmMemoria();
    private final UsuarioTemaRepositoryEmMemoria usuarioTemas = new UsuarioTemaRepositoryEmMemoria();
    private final GerenciarMeusTemasService service =
            new GerenciarMeusTemasService(temas, usuarioTemas, PlanosDeTeste.pro());
    private final GerenciarMeusTemasService doGratis =
            new GerenciarMeusTemasService(temas, usuarioTemas, PlanosDeTeste.gratis());
    private final UUID aluno = UUID.randomUUID();

    @Test
    void adicionaERemoveTemaPossivel() {
        var tema = temas.salvar(new Tema(null, "Possível", null, OrigemTema.PREVISAO, (short) 2026, true, null));

        service.adicionar(aluno, tema.id());
        assertThat(usuarioTemas.listarTemaIds(aluno)).containsExactly(tema.id());

        service.remover(aluno, tema.id());
        assertThat(usuarioTemas.listarTemaIds(aluno)).isEmpty();
    }

    @Test
    void adicionarDuasVezesEhIdempotente() {
        var tema = temas.salvar(new Tema(null, "Possível", null, OrigemTema.PREVISAO, (short) 2026, true, null));

        service.adicionar(aluno, tema.id());
        service.adicionar(aluno, tema.id());

        assertThat(usuarioTemas.listarTemaIds(aluno)).containsExactly(tema.id());
    }

    @Test
    void recusaTemaOficial() {
        var oficial = temas.salvar(new Tema(null, "Oficial", null, OrigemTema.ENEM_OFICIAL, (short) 2024, true, null));

        assertThatThrownBy(() -> service.adicionar(aluno, oficial.id()))
                .isInstanceOf(TemaNaoAdicionavelException.class);
        assertThat(usuarioTemas.listarTemaIds(aluno)).isEmpty();
    }

    @Test
    void temaInexistenteDaNaoEncontrado() {
        assertThatThrownBy(() -> service.adicionar(aluno, UUID.randomUUID()))
                .isInstanceOf(TemaNaoEncontradoException.class);
    }

    @Test
    void naoAdicionaTemaDesativadoMasPermiteRemover() {
        var desativado = temas.salvar(new Tema(null, "Desativado", null, OrigemTema.PREVISAO, (short) 2026, false, null));
        usuarioTemas.adicionar(aluno, desativado.id());

        assertThatThrownBy(() -> service.adicionar(UUID.randomUUID(), desativado.id()))
                .isInstanceOf(TemaNaoEncontradoException.class);

        service.remover(aluno, desativado.id());
        assertThat(usuarioTemas.listarTemaIds(aluno)).isEmpty();
    }

    // ---------- plano ----------

    @Test
    void alunoGratisNaoAdicionaENadaEGravado() {
        var tema = temas.salvar(new Tema(null, "Possível", null, OrigemTema.PREVISAO, (short) 2026, true, null));

        assertThatThrownBy(() -> doGratis.adicionar(aluno, tema.id()))
                .isInstanceOfSatisfying(RecursoExclusivoProException.class,
                        e -> assertThat(e.getRecurso()).isEqualTo(RecursoPro.TEMAS_POSSIVEIS));
        assertThat(usuarioTemas.listarTemaIds(aluno)).isEmpty();
    }

    @Test
    void alunoGratisConsegueRemoverOQueAdicionouQuandoEraPro() {
        var tema = temas.salvar(new Tema(null, "Possível", null, OrigemTema.PREVISAO, (short) 2026, true, null));
        service.adicionar(aluno, tema.id());

        doGratis.remover(aluno, tema.id());

        assertThat(usuarioTemas.listarTemaIds(aluno)).isEmpty();
    }
}
