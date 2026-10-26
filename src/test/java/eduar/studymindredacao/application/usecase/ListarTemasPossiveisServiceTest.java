package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.model.PrevisaoTema;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.ForcaEvidencia;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListarTemasPossiveisServiceTest {
    private final TemaRepositoryEmMemoria temas = new TemaRepositoryEmMemoria();
    private final PrevisaoTemaRepositoryEmMemoria previsoes = new PrevisaoTemaRepositoryEmMemoria();
    private final UsuarioTemaRepositoryEmMemoria usuarioTemas = new UsuarioTemaRepositoryEmMemoria();
    private final ListarTemasPossiveisService service =
            new ListarTemasPossiveisService(temas, previsoes, usuarioTemas, PlanosDeTeste.pro());
    private final UUID aluno = UUID.randomUUID();

    @Test
    void listaEmOrdemDeRankingEMarcaOsAdicionados() {
        var segundo = temaPossivel("Segundo", 2, ForcaEvidencia.MUITO_FORTE, true);
        var primeiro = temaPossivel("Primeiro", 1, ForcaEvidencia.MUITO_FORTE, true);
        usuarioTemas.adicionar(aluno, segundo.id());

        var lista = service.listar(aluno);

        assertThat(lista).extracting(t -> t.tema().titulo()).containsExactly("Primeiro", "Segundo");
        assertThat(lista).extracting(TemaPossivel::adicionado).containsExactly(false, true);
        assertThat(lista.get(0).tema().id()).isEqualTo(primeiro.id());
        assertThat(lista.get(0).previsao().argumentos()).containsExactly("Argumento");
    }

    @Test
    void ignoraTemasPossiveisDesativados() {
        temaPossivel("Ativo", 1, ForcaEvidencia.FORTE, true);
        temaPossivel("Desativado", 2, ForcaEvidencia.FORTE, false);

        assertThat(service.listar(aluno)).extracting(t -> t.tema().titulo()).containsExactly("Ativo");
    }

    @Test
    void naoMisturaTemasOficiais() {
        temas.salvar(new Tema(null, "Oficial", null, OrigemTema.ENEM_OFICIAL, (short) 2024, true, null));
        temaPossivel("Possível", 1, ForcaEvidencia.VALE_TREINAR, true);

        assertThat(service.listar(aluno)).extracting(t -> t.tema().titulo()).containsExactly("Possível");
    }

    @Test
    void alunoGratisNaoVeOsTemasPossiveis() {
        temaPossivel("Possível", 1, ForcaEvidencia.FORTE, true);
        var doGratis = new ListarTemasPossiveisService(temas, previsoes, usuarioTemas, PlanosDeTeste.gratis());

        assertThatThrownBy(() -> doGratis.listar(aluno))
                .isInstanceOfSatisfying(RecursoExclusivoProException.class,
                        e -> assertThat(e.getRecurso()).isEqualTo(RecursoPro.TEMAS_POSSIVEIS));
    }

    private Tema temaPossivel(String titulo, int ranking, ForcaEvidencia forca, boolean ativo) {
        var tema = temas.salvar(new Tema(null, titulo, null, OrigemTema.PREVISAO, (short) 2026, ativo, null));
        previsoes.salvar(new PrevisaoTema(tema.id(), ranking, forca, "Eixo", "Grupo", "Justificativa",
                List.of("Argumento"), List.of("Lei"), List.of("MEC")));
        return tema;
    }
}
