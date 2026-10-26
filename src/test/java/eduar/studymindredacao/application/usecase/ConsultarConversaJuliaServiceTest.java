package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.RecursoExclusivoProException;
import eduar.studymindredacao.domain.exception.RedacaoNaoEncontradaException;
import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.RecursoPro;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.model.enums.TipoRedacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultarConversaJuliaServiceTest {
    private static final PoliticaJulia POLITICA = PoliticaJulia.de(10, 500, 6, 24, 20, 100);

    private final RedacaoRepositoryEmMemoria redacoes = new RedacaoRepositoryEmMemoria();
    private final ConversaJuliaRepositoryEmMemoria conversas = new ConversaJuliaRepositoryEmMemoria();
    private final UsoIADiarioEmMemoria usos = new UsoIADiarioEmMemoria();
    private final RelogioAjustavel relogio = RelogioAjustavel.em("2026-10-22T15:00:00Z");
    private final ConsultarConversaJuliaService service =
            new ConsultarConversaJuliaService(redacoes, conversas, usos, POLITICA, PlanosDeTeste.pro(), relogio);

    private final UUID alunoId = UUID.randomUUID();
    private Redacao redacao;

    @BeforeEach
    void setUp() {
        redacao = redacoes.salvar(new Redacao(null, alunoId, UUID.randomUUID(), TipoRedacao.PRATICA, "texto",
                StatusRedacao.AVALIADA, OffsetDateTime.now(), OrigemRedacao.DIGITADO));
    }

    @Test
    void semConversaDevolveListaVaziaComOsLimitesCheios() {
        ConversaJuliaDetalhada detalhada = service.consultar(alunoId, redacao.id());

        assertThat(detalhada.mensagens()).isEmpty();
        assertThat(detalhada.restantesConversa()).isEqualTo(10);
        assertThat(detalhada.restantesHoje()).isEqualTo(20);
    }

    @Test
    void comConversaAtivaDevolveAsMensagensEmOrdemEOsRestantes() {
        salvarConversaComTrocas(2);
        usos.reservarMensagemJulia(alunoId, LocalDate.now(relogio), 20);
        usos.reservarMensagemJulia(alunoId, LocalDate.now(relogio), 20);

        ConversaJuliaDetalhada detalhada = service.consultar(alunoId, redacao.id());

        assertThat(detalhada.mensagens()).extracting(MensagemJulia::conteudo)
                .containsExactly("pergunta 1", "resposta 1", "pergunta 2", "resposta 2");
        assertThat(detalhada.restantesConversa()).isEqualTo(8);
        assertThat(detalhada.restantesHoje()).isEqualTo(18);
    }

    @Test
    void conversaExpiradaApareceVaziaESemSerAlterada() {
        salvarConversaComTrocas(1);
        relogio.avancar(Duration.ofHours(25));

        ConversaJuliaDetalhada detalhada = service.consultar(alunoId, redacao.id());

        assertThat(detalhada.mensagens()).isEmpty();
        assertThat(detalhada.restantesConversa()).isEqualTo(10);
        assertThat(conversas.buscarAtiva(alunoId, redacao.id())).isPresent();
    }

    @Test
    void redacaoDeOutroAlunoDa404() {
        assertThatThrownBy(() -> service.consultar(UUID.randomUUID(), redacao.id()))
                .isInstanceOf(RedacaoNaoEncontradaException.class);
    }

    @Test
    void redacaoInexistenteDa404() {
        assertThatThrownBy(() -> service.consultar(alunoId, UUID.randomUUID()))
                .isInstanceOf(RedacaoNaoEncontradaException.class);
    }

    @Test
    void conversaDeOutraRedacaoDoMesmoAlunoNaoAparece() {
        salvarConversaComTrocas(1);
        var outra = redacoes.salvar(new Redacao(null, alunoId, UUID.randomUUID(), TipoRedacao.PRATICA, "texto",
                StatusRedacao.AVALIADA, OffsetDateTime.now(), OrigemRedacao.DIGITADO));

        assertThat(service.consultar(alunoId, outra.id()).mensagens()).isEmpty();
    }

    @Test
    void alunoGratisNaoLeAConversaNemAsAntigas() {
        salvarConversaComTrocas(1);
        var doGratis = new ConsultarConversaJuliaService(redacoes, conversas, usos, POLITICA,
                PlanosDeTeste.gratis(), relogio);

        assertThatThrownBy(() -> doGratis.consultar(alunoId, redacao.id()))
                .isInstanceOfSatisfying(RecursoExclusivoProException.class,
                        e -> assertThat(e.getRecurso()).isEqualTo(RecursoPro.JULIA));
    }

    @Test
    void alunoGratisComRedacaoDeOutroContinuaRecebendo404() {
        var doGratis = new ConsultarConversaJuliaService(redacoes, conversas, usos, POLITICA,
                PlanosDeTeste.gratis(), relogio);

        assertThatThrownBy(() -> doGratis.consultar(UUID.randomUUID(), redacao.id()))
                .isInstanceOf(RedacaoNaoEncontradaException.class);
    }

    private void salvarConversaComTrocas(int quantidade) {
        var agora = relogio.instant();
        var conversa = ConversaJulia.iniciar(alunoId, redacao.id(), agora);
        for (int i = 1; i <= quantidade; i++) {
            conversa.registrarTroca(
                    MensagemJulia.doUsuario("pergunta " + i, 500, agora),
                    MensagemJulia.daJulia(new RespostaJulia("resposta " + i, 900, 120), agora),
                    POLITICA.limites(), agora);
        }
        conversas.salvar(conversa);
    }
}
