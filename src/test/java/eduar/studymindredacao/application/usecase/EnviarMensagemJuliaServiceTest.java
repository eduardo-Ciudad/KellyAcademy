package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.IAOcupadaException;
import eduar.studymindredacao.domain.exception.IAOcupadaException.Motivo;
import eduar.studymindredacao.domain.exception.RedacaoNaoEncontradaException;
import eduar.studymindredacao.domain.julia.ContextoJulia;
import eduar.studymindredacao.domain.julia.ConversaJulia;
import eduar.studymindredacao.domain.julia.JuliaIndisponivelException;
import eduar.studymindredacao.domain.julia.LimiteJuliaExcedidoException;
import eduar.studymindredacao.domain.julia.MensagemJulia;
import eduar.studymindredacao.domain.julia.PapelMensagem;
import eduar.studymindredacao.domain.julia.PerguntaInvalidaException;
import eduar.studymindredacao.domain.julia.RedacaoSemCorrecaoException;
import eduar.studymindredacao.domain.julia.RespostaJulia;
import eduar.studymindredacao.domain.julia.StatusConversa;
import eduar.studymindredacao.domain.julia.TipoLimiteJulia;
import eduar.studymindredacao.domain.model.Avaliacao;
import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.model.enums.TipoRedacao;
import eduar.studymindredacao.domain.port.JuliaIAPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnviarMensagemJuliaServiceTest {
    private static final PoliticaJulia POLITICA = PoliticaJulia.de(10, 500, 6, 24, 20, 100);

    private final RedacaoRepositoryEmMemoria redacoes = new RedacaoRepositoryEmMemoria();
    private final AvaliacaoRepositoryEmMemoria avaliacoes = new AvaliacaoRepositoryEmMemoria();
    private final TemaRepositoryEmMemoria temas = new TemaRepositoryEmMemoria();
    private final ConversaJuliaRepositoryEmMemoria conversas = new ConversaJuliaRepositoryEmMemoria();
    private final UsoIADiarioEmMemoria usos = new UsoIADiarioEmMemoria();
    private final UsoIAGlobalEmMemoria global = new UsoIAGlobalEmMemoria();
    private final JuliaIAFake julia = new JuliaIAFake();
    private final RelogioAjustavel relogio = RelogioAjustavel.em("2026-10-22T15:00:00Z");

    private final UUID alunoId = UUID.randomUUID();
    private Redacao redacao;
    private UUID temaId;
    private EnviarMensagemJuliaService service;

    @BeforeEach
    void setUp() {
        var tema = temas.salvar(new Tema(null, "Desafios para a formação educacional de surdos no Brasil", null,
                OrigemTema.ENEM_OFICIAL, (short) 2017, true, null));
        temaId = tema.id();
        redacao = redacaoCorrigida(alunoId, tema.id());
        service = criarService(POLITICA);
    }

    // ---------- sucesso ----------

    @Test
    void primeiraPerguntaSalvaATrocaComTokensEDevolveOsRestantes() {
        TrocaJulia troca = service.enviar(alunoId, redacao.id(), "  Por que fiquei com 80 na C3?  ");

        assertThat(troca.pergunta().id()).isNotNull();
        assertThat(troca.pergunta().papel()).isEqualTo(PapelMensagem.USUARIO);
        assertThat(troca.pergunta().conteudo()).isEqualTo("Por que fiquei com 80 na C3?");
        assertThat(troca.resposta().id()).isNotNull();
        assertThat(troca.resposta().papel()).isEqualTo(PapelMensagem.JULIA);
        assertThat(troca.resposta().tokensEntrada()).isEqualTo(900);
        assertThat(troca.resposta().tokensSaida()).isEqualTo(120);
        assertThat(troca.restantesConversa()).isEqualTo(9);
        assertThat(troca.restantesHoje()).isEqualTo(19);

        ConversaJulia salva = conversas.buscarAtiva(alunoId, redacao.id()).orElseThrow();
        assertThat(salva.getMensagens()).hasSize(2);
        assertThat(usadasHoje()).isEqualTo(1);
    }

    @Test
    void aJuliaRecebeOContextoDaRedacaoEAPerguntaSemHistoricoNaPrimeiraVez() {
        service.enviar(alunoId, redacao.id(), "Por que fiquei com 80 na C3?");

        var chamada = julia.chamadas().get(0);
        assertThat(chamada.contexto().tema()).isEqualTo("Desafios para a formação educacional de surdos no Brasil");
        assertThat(chamada.contexto().diagnostico()).isEqualTo("A maior fragilidade está na C3.");
        assertThat(chamada.historico()).isEmpty();
        assertThat(chamada.pergunta()).isEqualTo("Por que fiquei com 80 na C3?");
    }

    @Test
    void segundaPerguntaContinuaAMesmaConversaEEnviaOHistorico() {
        TrocaJulia primeira = service.enviar(alunoId, redacao.id(), "Por que fiquei com 80 na C3?");
        TrocaJulia segunda = service.enviar(alunoId, redacao.id(), "E como eu melhoro?");

        assertThat(julia.chamadas().get(1).historico())
                .extracting(MensagemJulia::conteudo)
                .containsExactly("Por que fiquei com 80 na C3?", JuliaIAFake.RESPOSTA_PADRAO.texto());
        assertThat(segunda.restantesConversa()).isEqualTo(8);
        assertThat(segunda.restantesHoje()).isEqualTo(18);
        assertThat(conversas.todas()).hasSize(1);
        assertThat(conversas.todas().get(0).getMensagens()).hasSize(4);
        assertThat(segunda.pergunta().id()).isNotEqualTo(primeira.pergunta().id());
    }

    @Test
    void historicoEnviadoAIANuncaPassaDoLimiteEComecaPeloAluno() {
        for (int i = 1; i <= 5; i++) {
            service.enviar(alunoId, redacao.id(), "pergunta " + i);
        }

        List<MensagemJulia> historico = julia.chamadas().get(4).historico();
        assertThat(historico).hasSize(6);
        assertThat(historico.get(0).papel()).isEqualTo(PapelMensagem.USUARIO);
        assertThat(historico.get(0).conteudo()).isEqualTo("pergunta 2");
    }

    // ---------- conversa expirada ----------

    @Test
    void conversaParadaMaisQueOLimiteEEncerradaEUmaNovaComeca() {
        service.enviar(alunoId, redacao.id(), "pergunta antiga");
        relogio.avancar(Duration.ofHours(25));

        TrocaJulia troca = service.enviar(alunoId, redacao.id(), "pergunta nova");

        assertThat(troca.restantesConversa()).isEqualTo(9);
        assertThat(julia.chamadas().get(1).historico()).isEmpty();
        var todas = conversas.todas();
        assertThat(todas).hasSize(2);
        assertThat(todas.get(0).getStatus()).isEqualTo(StatusConversa.ENCERRADA);
        assertThat(todas.get(0).getEncerradaEm()).isEqualTo(relogio.instant());
        assertThat(todas.get(1).getStatus()).isEqualTo(StatusConversa.ATIVA);
        assertThat(todas.get(1).getMensagens()).hasSize(2);
    }

    @Test
    void conversaDentroDoLimiteDeInatividadeContinuaAMesma() {
        service.enviar(alunoId, redacao.id(), "pergunta 1");
        relogio.avancar(Duration.ofHours(23));

        service.enviar(alunoId, redacao.id(), "pergunta 2");

        assertThat(conversas.todas()).hasSize(1);
        assertThat(conversas.todas().get(0).getMensagens()).hasSize(4);
    }

    // ---------- recusas antes de gastar cota ----------

    @Test
    void redacaoDeOutroAlunoDa404SemChamarAIANemGastarCota() {
        assertThatThrownBy(() -> service.enviar(UUID.randomUUID(), redacao.id(), "Por que fiquei com 80?"))
                .isInstanceOf(RedacaoNaoEncontradaException.class);

        assertThat(julia.chamadas()).isEmpty();
        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isZero();
    }

    @Test
    void redacaoInexistenteDa404() {
        assertThatThrownBy(() -> service.enviar(alunoId, UUID.randomUUID(), "Por que fiquei com 80?"))
                .isInstanceOf(RedacaoNaoEncontradaException.class);
    }

    @Test
    void redacaoSemCorrecaoERecusadaSemReservarVaga() {
        var semCorrecao = redacoes.salvar(new Redacao(null, alunoId, UUID.randomUUID(), TipoRedacao.PRATICA,
                "texto", StatusRedacao.EM_AVALIACAO, OffsetDateTime.now(), OrigemRedacao.DIGITADO));

        assertThatThrownBy(() -> service.enviar(alunoId, semCorrecao.id(), "Por que fiquei com 80?"))
                .isInstanceOf(RedacaoSemCorrecaoException.class);

        assertThat(usadasHoje()).isZero();
        assertThat(julia.chamadas()).isEmpty();
    }

    @Test
    void perguntaVaziaOuLongaDemaisERecusadaSemReservarVaga() {
        assertThatThrownBy(() -> service.enviar(alunoId, redacao.id(), "   "))
                .isInstanceOf(PerguntaInvalidaException.class);
        assertThatThrownBy(() -> service.enviar(alunoId, redacao.id(), "a".repeat(501)))
                .isInstanceOf(PerguntaInvalidaException.class);

        assertThat(usadasHoje()).isZero();
        assertThat(julia.chamadas()).isEmpty();
        assertThat(conversas.todas()).isEmpty();
    }

    @Test
    void conversaNoLimiteDePerguntasERecusadaSemReservarVaga() {
        for (int i = 1; i <= 10; i++) {
            service.enviar(alunoId, redacao.id(), "pergunta " + i);
        }

        assertThatThrownBy(() -> service.enviar(alunoId, redacao.id(), "pergunta 11"))
                .isInstanceOf(LimiteJuliaExcedidoException.class)
                .extracting("tipo").isEqualTo(TipoLimiteJulia.CONVERSA);

        assertThat(usadasHoje()).isEqualTo(10);
        assertThat(julia.chamadas()).hasSize(10);
        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isEqualTo(10);
    }

    @Test
    void limiteDiarioDoAlunoERecusadoSemChamarAIA() {
        for (int i = 0; i < 20; i++) {
            usos.reservarMensagemJulia(alunoId, hoje(), 20);
        }

        assertThatThrownBy(() -> service.enviar(alunoId, redacao.id(), "Por que fiquei com 80?"))
                .isInstanceOf(LimiteJuliaExcedidoException.class)
                .extracting("tipo").isEqualTo(TipoLimiteJulia.DIARIO_USUARIO);

        assertThat(julia.chamadas()).isEmpty();
        assertThat(usadasHoje()).isEqualTo(20);
    }

    @Test
    void tetoGlobalERecusadoAntesDeReservarAVagaDoAluno() {
        // O teto do sistema agora é o contador global (V24), não mais a soma da tabela de cada aluno
        global.reservar(RecursoIA.JULIA, hoje(), 2);
        global.reservar(RecursoIA.JULIA, hoje(), 2);
        var comTetoBaixo = criarService(PoliticaJulia.de(10, 500, 6, 24, 20, 2));

        assertThatThrownBy(() -> comTetoBaixo.enviar(alunoId, redacao.id(), "Por que fiquei com 80?"))
                .isInstanceOf(LimiteJuliaExcedidoException.class)
                .extracting("tipo").isEqualTo(TipoLimiteJulia.GLOBAL);

        assertThat(usadasHoje()).isZero();
        assertThat(julia.chamadas()).isEmpty();
        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isEqualTo(2);
    }

    // ---------- falhas depois da reserva devolvem a vaga ----------

    @Test
    void falhaDaJuliaDevolveAVagaENaoSalvaNada() {
        var falha = new JuliaIndisponivelException("Gemini fora do ar");
        julia.falharCom(falha);

        assertThatThrownBy(() -> service.enviar(alunoId, redacao.id(), "Por que fiquei com 80?"))
                .isSameAs(falha);

        assertThat(usadasHoje()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isZero();
        assertThat(conversas.todas()).isEmpty();
    }

    @Test
    void falhaInesperadaDaIAViraJuliaIndisponivelEDevolveAVaga() {
        var causa = new IllegalStateException("resposta estranha");
        julia.falharCom(causa);

        assertThatThrownBy(() -> service.enviar(alunoId, redacao.id(), "Por que fiquei com 80?"))
                .isInstanceOf(JuliaIndisponivelException.class)
                .hasMessage(EnviarMensagemJuliaService.MENSAGEM_INDISPONIVEL)
                .hasCause(causa);

        assertThat(usadasHoje()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isZero();
    }

    @Test
    void falhaAoSalvarAConversaDevolveAVaga() {
        var falha = new IllegalStateException("banco fora do ar");
        conversas.falharAoSalvar(falha);

        assertThatThrownBy(() -> service.enviar(alunoId, redacao.id(), "Por que fiquei com 80?"))
                .isSameAs(falha);

        assertThat(usadasHoje()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isZero();
    }

    // ---------- concorrência (KELLY-SEC-002 e SEC-003) ----------

    @Test
    void primeiraPerguntaContaUmaVagaGlobal() {
        service.enviar(alunoId, redacao.id(), "Por que fiquei com 80 na C3?");

        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isEqualTo(1);
        assertThat(usadasHoje()).isEqualTo(1);
    }

    @Test
    void perguntaSimultaneaNaoFuraOLimiteDaConversa() throws Exception {
        for (int i = 1; i <= 9; i++) {
            service.enviar(alunoId, redacao.id(), "pergunta " + i);
        }
        var presa = new JuliaIAPresa();
        var comJuliaPresa = criarService(POLITICA, presa, LimitadoresDeTeste.padrao());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<TrocaJulia> decima = executor.submit(() -> comJuliaPresa.enviar(alunoId, redacao.id(), "pergunta 10"));
            presa.esperarEntrada();

            // Antes: esta lia a mesma conversa com 9 perguntas, passava no limite e virava a 11ª chamada paga
            assertThatThrownBy(() -> comJuliaPresa.enviar(alunoId, redacao.id(), "pergunta simultânea"))
                    .isInstanceOfSatisfying(IAOcupadaException.class, e -> {
                        assertThat(e.getMotivo()).isEqualTo(Motivo.ALUNO_EM_ANDAMENTO);
                        assertThat(e.getRecurso()).isEqualTo(RecursoIA.JULIA);
                    });
            assertThat(usadasHoje()).isEqualTo(10);
            assertThat(presa.chamadas).hasValue(1);

            presa.soltar.countDown();
            assertThat(decima.get(10, TimeUnit.SECONDS).restantesConversa()).isZero();
        } finally {
            presa.soltar.countDown();
            executor.shutdownNow();
        }

        // Com a 10ª já salva, a próxima é recusada pelo limite da conversa, sem chamar a IA
        assertThatThrownBy(() -> comJuliaPresa.enviar(alunoId, redacao.id(), "pergunta 11"))
                .isInstanceOf(LimiteJuliaExcedidoException.class)
                .extracting("tipo").isEqualTo(TipoLimiteJulia.CONVERSA);

        assertThat(julia.chamadas().size() + presa.chamadas.get()).isEqualTo(10);
        assertThat(usadasHoje()).isEqualTo(10);
        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isEqualTo(10);
        assertThat(conversas.todas()).singleElement()
                .satisfies(c -> assertThat(c.getMensagens()).hasSize(20));
    }

    @Test
    void perguntaSobreOutraRedacaoTambemEsperaATravaDoAluno() throws Exception {
        var outraRedacao = redacaoCorrigida(alunoId, temaId);
        var presa = new JuliaIAPresa();
        var comJuliaPresa = criarService(POLITICA, presa, LimitadoresDeTeste.padrao());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<TrocaJulia> primeira = executor.submit(() -> comJuliaPresa.enviar(alunoId, redacao.id(), "sobre a A"));
            presa.esperarEntrada();

            assertThatThrownBy(() -> comJuliaPresa.enviar(alunoId, outraRedacao.id(), "sobre a B"))
                    .isInstanceOfSatisfying(IAOcupadaException.class,
                            e -> assertThat(e.getMotivo()).isEqualTo(Motivo.ALUNO_EM_ANDAMENTO));
            assertThat(usadasHoje()).isEqualTo(1);

            presa.soltar.countDown();
            primeira.get(10, TimeUnit.SECONDS);
        } finally {
            presa.soltar.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void falhaDaJuliaSoltaATravaParaOAlunoPerguntarDeNovo() {
        julia.falharCom(new JuliaIndisponivelException("Gemini fora do ar"));
        assertThatThrownBy(() -> service.enviar(alunoId, redacao.id(), "Por que fiquei com 80?"))
                .isInstanceOf(JuliaIndisponivelException.class);
        julia.falharCom(null);

        TrocaJulia troca = service.enviar(alunoId, redacao.id(), "Por que fiquei com 80?");

        assertThat(troca).isNotNull();
        assertThat(usadasHoje()).isEqualTo(1);
        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, hoje())).isEqualTo(1);
    }

    @Test
    void perguntaInvalidaNaoDisputaATrava() throws Exception {
        var presa = new JuliaIAPresa();
        var comJuliaPresa = criarService(POLITICA, presa, LimitadoresDeTeste.padrao());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<TrocaJulia> primeira = executor.submit(() -> comJuliaPresa.enviar(alunoId, redacao.id(), "pergunta"));
            presa.esperarEntrada();

            // Com a trava ocupada, a pergunta vazia recebe o erro de validação, não "em andamento"
            assertThatThrownBy(() -> comJuliaPresa.enviar(alunoId, redacao.id(), "   "))
                    .isInstanceOf(PerguntaInvalidaException.class);

            presa.soltar.countDown();
            primeira.get(10, TimeUnit.SECONDS);
        } finally {
            presa.soltar.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void corrigirNaoBloqueiaPerguntarAJulia() {
        var limitador = LimitadoresDeTeste.padrao();
        var comLimitadorCompartilhado = criarService(POLITICA, julia, limitador);

        try (var correcao = limitador.adquirir(alunoId, RecursoIA.CORRECAO)) {
            assertThat(comLimitadorCompartilhado.enviar(alunoId, redacao.id(), "Por que fiquei com 80?")).isNotNull();
        }
    }

    // ---------- apoio ----------

    private EnviarMensagemJuliaService criarService(PoliticaJulia politica) {
        return criarService(politica, julia, LimitadoresDeTeste.padrao());
    }

    private EnviarMensagemJuliaService criarService(PoliticaJulia politica, JuliaIAPort ia,
                                                    LimitadorConcorrenciaIA limitador) {
        return new EnviarMensagemJuliaService(redacoes, new MontadorContextoJulia(avaliacoes, temas), conversas,
                usos, ia, new ReservadorCotaIA(global, usos), limitador, politica, relogio);
    }

    /** Júlia que para dentro da chamada até o teste soltar: simula uma pergunta em andamento. */
    private static final class JuliaIAPresa implements JuliaIAPort {
        final CountDownLatch dentro = new CountDownLatch(1);
        final CountDownLatch soltar = new CountDownLatch(1);
        final AtomicInteger chamadas = new AtomicInteger();

        @Override
        public RespostaJulia responder(ContextoJulia contexto, List<MensagemJulia> historico, String pergunta) {
            chamadas.incrementAndGet();
            dentro.countDown();
            try {
                if (!soltar.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Júlia presa por tempo demais no teste");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return JuliaIAFake.RESPOSTA_PADRAO;
        }

        void esperarEntrada() throws InterruptedException {
            assertThat(dentro.await(5, TimeUnit.SECONDS)).as("a pergunta não chegou à IA").isTrue();
        }
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    private int usadasHoje() {
        return usos.contarMensagensJulia(alunoId, hoje());
    }

    private Redacao redacaoCorrigida(UUID dono, UUID temaId) {
        var salva = redacoes.salvar(new Redacao(null, dono, temaId, TipoRedacao.PRATICA, "Texto da redação do aluno.",
                StatusRedacao.AVALIADA, OffsetDateTime.now(), OrigemRedacao.DIGITADO));
        var competencias = List.of(
                new CompetenciaAvaliada(1, 160, "n1", "Poucos desvios.", List.of()),
                new CompetenciaAvaliada(2, 160, "n2", "Repertório pouco explorado.", List.of()),
                new CompetenciaAvaliada(3, 80, "n3", "Argumentos previsíveis.", List.of("tese sem defesa")),
                new CompetenciaAvaliada(4, 160, "n4", "Boa coesão.", List.of()),
                new CompetenciaAvaliada(5, 120, "n5", "Falta detalhar a finalidade.", List.of())
        );
        avaliacoes.salvar(new Avaliacao(null, salva.id(), (short) 160, (short) 160, (short) 80, (short) 160,
                (short) 120, null, false, null, competencias, List.of(), List.of(),
                "A maior fragilidade está na C3.", null, null, null, null, null));
        return salva;
    }
}
