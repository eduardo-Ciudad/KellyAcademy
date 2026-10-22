package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.julia.CompetenciaContexto;
import eduar.studymindredacao.domain.julia.ContextoJulia;
import eduar.studymindredacao.domain.julia.RedacaoSemCorrecaoException;
import eduar.studymindredacao.domain.model.Avaliacao;
import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.model.enums.TipoRedacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MontadorContextoJuliaTest {
    private final RedacaoRepositoryEmMemoria redacoes = new RedacaoRepositoryEmMemoria();
    private final AvaliacaoRepositoryEmMemoria avaliacoes = new AvaliacaoRepositoryEmMemoria();
    private final TemaRepositoryEmMemoria temas = new TemaRepositoryEmMemoria();
    private final MontadorContextoJulia montador = new MontadorContextoJulia(avaliacoes, temas);

    private Tema surdos;

    @BeforeEach
    void setUp() {
        surdos = temas.salvar(new Tema(null, "Desafios para a formação educacional de surdos no Brasil", null,
                OrigemTema.ENEM_OFICIAL, (short) 2017, true, null));
    }

    @Test
    void deveMontarOContextoComTemaTextoNotaCompetenciasEDiagnostico() {
        var redacao = redacao(surdos.id(), StatusRedacao.AVALIADA);
        avaliacoes.salvar(avaliacaoCompleta(redacao));

        ContextoJulia contexto = montador.montar(redacao);

        assertThat(contexto.tema()).isEqualTo("Desafios para a formação educacional de surdos no Brasil");
        assertThat(contexto.textoRedacao()).isEqualTo("Texto da redação do aluno.");
        assertThat(contexto.notaTotal()).isEqualTo(680);
        assertThat(contexto.diagnostico()).isEqualTo("A maior fragilidade está na C3.");
        assertThat(contexto.possuiAjusteNota()).isFalse();
        assertThat(contexto.competencias()).extracting(CompetenciaContexto::numero).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void comentarioVemDoResumoEProblemasVemDosProblemasIdentificados() {
        var redacao = redacao(surdos.id(), StatusRedacao.AVALIADA);
        avaliacoes.salvar(avaliacaoCompleta(redacao));

        CompetenciaContexto c3 = montador.montar(redacao).competencias().get(2);

        assertThat(c3.nota()).isEqualTo(80);
        assertThat(c3.comentario()).isEqualTo("Argumentos previsíveis.");
        assertThat(c3.problemas()).containsExactly("segundo desenvolvimento repete o primeiro",
                "falta de intérpretes fica só na afirmação");
    }

    @Test
    void temaInexistenteUsaUmTituloPadrao() {
        var redacao = redacao(UUID.randomUUID(), StatusRedacao.AVALIADA);
        avaliacoes.salvar(avaliacaoCompleta(redacao));

        assertThat(montador.montar(redacao).tema()).isEqualTo(MontadorContextoJulia.TEMA_NAO_INFORMADO);
    }

    @ParameterizedTest
    @EnumSource(value = StatusRedacao.class, names = "AVALIADA", mode = EnumSource.Mode.EXCLUDE)
    void redacaoAindaNaoCorrigidaERecusada(StatusRedacao status) {
        var redacao = redacao(surdos.id(), status);

        assertThatThrownBy(() -> montador.montar(redacao)).isInstanceOf(RedacaoSemCorrecaoException.class);
    }

    @Test
    void redacaoAvaliadaSemAvaliacaoSalvaERecusada() {
        var redacao = redacao(surdos.id(), StatusRedacao.AVALIADA);

        assertThatThrownBy(() -> montador.montar(redacao)).isInstanceOf(RedacaoSemCorrecaoException.class);
    }

    @Test
    void redacaoAnuladaERecusada() {
        var redacao = redacao(surdos.id(), StatusRedacao.AVALIADA);
        short zero = 0;
        avaliacoes.salvar(new Avaliacao(null, redacao.id(), zero, zero, zero, zero, zero, null, true,
                "Fuga total ao tema", null, null, null, "Redação anulada.", null, null, null, null, null));

        assertThatThrownBy(() -> montador.montar(redacao)).isInstanceOf(RedacaoSemCorrecaoException.class);
    }

    @Test
    void avaliacaoAntigaSemAsCompetenciasDetalhadasERecusada() {
        var redacao = redacao(surdos.id(), StatusRedacao.AVALIADA);
        short n = 120;
        avaliacoes.salvar(new Avaliacao(null, redacao.id(), n, n, n, n, n, null, false, null,
                null, null, null, "diagnóstico", null, null, null, null, null));

        assertThatThrownBy(() -> montador.montar(redacao)).isInstanceOf(RedacaoSemCorrecaoException.class);
    }

    private Redacao redacao(UUID temaId, StatusRedacao status) {
        return redacoes.salvar(new Redacao(null, UUID.randomUUID(), temaId, TipoRedacao.PRATICA,
                "Texto da redação do aluno.", status, OffsetDateTime.now(), OrigemRedacao.DIGITADO));
    }

    private static Avaliacao avaliacaoCompleta(Redacao redacao) {
        var competencias = List.of(
                new CompetenciaAvaliada(1, 160, "poucos desvios", "Poucos desvios.", List.of()),
                new CompetenciaAvaliada(2, 160, "repertório pertinente", "Repertório pouco explorado.", List.of()),
                new CompetenciaAvaliada(3, 80, "argumentos previsíveis", "Argumentos previsíveis.",
                        List.of("segundo desenvolvimento repete o primeiro", "falta de intérpretes fica só na afirmação")),
                new CompetenciaAvaliada(4, 160, "boa coesão", "Boa variedade de conectivos.", List.of()),
                new CompetenciaAvaliada(5, 120, "proposta incompleta", "Falta detalhar a finalidade.", List.of())
        );
        return new Avaliacao(null, redacao.id(), (short) 160, (short) 160, (short) 80, (short) 160, (short) 120,
                null, false, null, competencias, List.of(), List.of(), "A maior fragilidade está na C3.",
                null, null, null, null, null);
    }
}
