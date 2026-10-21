package eduar.studymindredacao.domain.julia;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversaJuliaTest {

    private static final Instant AGORA = Instant.parse("2026-10-21T20:00:00Z");
    private static final LimitesJulia LIMITES = new LimitesJulia(10, 500, 6, Duration.ofHours(24));
    private static final UUID USUARIO_ID = UUID.randomUUID();
    private static final UUID REDACAO_ID = UUID.randomUUID();

    @Test
    void deveNascerAtivaEVazia() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);

        assertThat(conversa.getStatus()).isEqualTo(StatusConversa.ATIVA);
        assertThat(conversa.getMensagens()).isEmpty();
        assertThat(conversa.getCriadaEm()).isEqualTo(AGORA);
        assertThat(conversa.getUltimaAtividadeEm()).isEqualTo(AGORA);
        assertThat(conversa.perguntasRestantes(LIMITES)).isEqualTo(10);
    }

    @Test
    void deveRegistrarPerguntaERespostaNaOrdemEAtualizarAtividade() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);
        Instant depois = AGORA.plusSeconds(5);

        conversa.registrarTroca(pergunta("Por que perdi pontos na C3?"), resposta("Na C3..."), LIMITES, depois);

        assertThat(conversa.getMensagens())
                .extracting(MensagemJulia::papel)
                .containsExactly(PapelMensagem.USUARIO, PapelMensagem.JULIA);
        assertThat(conversa.getUltimaAtividadeEm()).isEqualTo(depois);
        assertThat(conversa.perguntasRestantes(LIMITES)).isEqualTo(9);
    }

    @Test
    void deveBloquearPerguntaAlemDoLimiteDaConversa() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);
        registrarTrocas(conversa, 10);

        assertThat(conversa.perguntasRestantes(LIMITES)).isZero();
        assertThatThrownBy(() -> conversa.registrarTroca(pergunta("mais uma"), resposta("..."), LIMITES, AGORA))
                .isInstanceOf(LimiteJuliaExcedidoException.class)
                .extracting("tipo")
                .isEqualTo(TipoLimiteJulia.CONVERSA);
        assertThat(conversa.getMensagens()).hasSize(20);
    }

    @Test
    void garantirQuePodeReceberPerguntaDeveFalharNoLimiteSemAlterarAConversa() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);
        registrarTrocas(conversa, 10);

        assertThatThrownBy(() -> conversa.garantirQuePodeReceberPergunta(LIMITES))
                .isInstanceOf(LimiteJuliaExcedidoException.class);
        assertThat(conversa.getMensagens()).hasSize(20);
    }

    @Test
    void naoDeveAceitarPerguntaEmConversaEncerrada() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);
        conversa.encerrar(AGORA);

        assertThatThrownBy(() -> conversa.registrarTroca(pergunta("oi"), resposta("..."), LIMITES, AGORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encerrarDeveSerIdempotente() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);

        conversa.encerrar(AGORA);
        conversa.encerrar(AGORA.plus(Duration.ofHours(1)));

        assertThat(conversa.getStatus()).isEqualTo(StatusConversa.ENCERRADA);
        assertThat(conversa.getEncerradaEm()).isEqualTo(AGORA);
    }

    @Test
    void deveRejeitarTrocaComPapeisInvertidos() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);

        assertThatThrownBy(() -> conversa.registrarTroca(resposta("..."), pergunta("oi"), LIMITES, AGORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(conversa.getMensagens()).isEmpty();
    }

    @Test
    void deveExpirarSomenteDepoisDaInatividadeMaxima() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);
        Duration maxima = LIMITES.inatividadeMaxima();

        assertThat(conversa.expirada(AGORA.plus(Duration.ofHours(23)).plus(Duration.ofMinutes(59)), maxima)).isFalse();
        assertThat(conversa.expirada(AGORA.plus(Duration.ofHours(24)), maxima)).isFalse();
        assertThat(conversa.expirada(AGORA.plus(Duration.ofHours(24)).plusSeconds(1), maxima)).isTrue();
    }

    @Test
    void historicoRecenteDeveTrazerAsUltimasMensagensEmOrdem() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);
        registrarTrocas(conversa, 5);

        List<MensagemJulia> historico = conversa.historicoRecente(6);

        assertThat(historico)
                .extracting(MensagemJulia::conteudo)
                .containsExactly("pergunta 3", "resposta 3", "pergunta 4", "resposta 4", "pergunta 5", "resposta 5");
        assertThat(historico.get(0).ehDoUsuario()).isTrue();
    }

    @Test
    void historicoRecenteComPoucasMensagensDeveDevolverTodas() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);
        registrarTrocas(conversa, 1);

        assertThat(conversa.historicoRecente(6)).hasSize(2);
    }

    @Test
    void mensagensNaoPodemSerAlteradasPorFora() {
        ConversaJulia conversa = ConversaJulia.iniciar(USUARIO_ID, REDACAO_ID, AGORA);

        assertThatThrownBy(() -> conversa.getMensagens().add(pergunta("oi")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static MensagemJulia pergunta(String texto) {
        return MensagemJulia.doUsuario(texto, 500, AGORA);
    }

    private static MensagemJulia resposta(String texto) {
        return MensagemJulia.daJulia(new RespostaJulia(texto, 900, 120), AGORA);
    }

    private static void registrarTrocas(ConversaJulia conversa, int quantidade) {
        for (int i = 1; i <= quantidade; i++) {
            conversa.registrarTroca(pergunta("pergunta " + i), resposta("resposta " + i), LIMITES, AGORA);
        }
    }
}