package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.IAOcupadaException;
import eduar.studymindredacao.domain.exception.IAOcupadaException.Motivo;
import eduar.studymindredacao.domain.exception.ImagemInvalidaException;
import eduar.studymindredacao.domain.exception.ImagemNaoReconhecidaException;
import eduar.studymindredacao.domain.exception.LimiteDiarioAtingidoException;
import eduar.studymindredacao.domain.exception.LimiteGlobalAtingidoException;
import eduar.studymindredacao.domain.exception.TranscricaoIAException;
import eduar.studymindredacao.domain.model.ImagemRedacao;
import eduar.studymindredacao.domain.model.ImagensDeTeste;
import eduar.studymindredacao.domain.model.ResultadoTranscricao;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.port.TranscricaoIAPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscreverRedacaoServiceTest {
    private static final int LIMITE = 3;
    private static final int LIMITE_GLOBAL = 30;
    // 01:30 UTC do dia 29 = 22:30 do dia 28 em São Paulo
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-29T01:30:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate HOJE_EM_SP = LocalDate.of(2026, 9, 28);

    private final UUID alunoId = UUID.randomUUID();
    private final UsoIADiarioEmMemoria uso = new UsoIADiarioEmMemoria();
    private final UsoIAGlobalEmMemoria global = new UsoIAGlobalEmMemoria();
    private final TranscricaoIAFake ia = new TranscricaoIAFake();
    private final TranscreverRedacaoService service = novoServico(ia, LimitadoresDeTeste.padrao(), LIMITE_GLOBAL);
    private final ImagemRedacao foto = ImagemRedacao.de(ImagensDeTeste.jpeg());

    private TranscreverRedacaoService novoServico(TranscricaoIAPort transcricaoIA, LimitadorConcorrenciaIA limitador,
                                                  int tetoGlobal) {
        return new TranscreverRedacaoService(uso, transcricaoIA, new ReservadorCotaIA(global, uso), limitador,
                RELOGIO, LIMITE, tetoGlobal);
    }

    @Test
    void transcreveEContaAVagaEOsTokensNoDiaDeSaoPaulo() {
        var resultado = service.transcrever(alunoId, List.of(foto));

        assertThat(resultado.linhas()).containsExactly("A educação no Brasil", "enfrenta desafios.");
        var doDia = uso.usoDoDia(alunoId, HOJE_EM_SP);
        assertThat(doDia.qtdTranscricoes()).isEqualTo(1);
        assertThat(doDia.tokensEntrada()).isEqualTo(TranscricaoIAFake.TOKENS_ENTRADA);
        assertThat(doDia.tokensSaida()).isEqualTo(TranscricaoIAFake.TOKENS_SAIDA);
        assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, HOJE_EM_SP)).isEqualTo(1);
    }

    @Test
    void naoGastaVagaDeCorrecao() {
        service.transcrever(alunoId, List.of(foto));

        assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdCorrecoes()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE_EM_SP)).isZero();
    }

    @Test
    void aceitaDuasFotosNaOrdemRecebida() {
        var segunda = ImagemRedacao.de(ImagensDeTeste.png());

        service.transcrever(alunoId, List.of(foto, segunda));

        assertThat(ia.chamadas()).containsExactly(List.of(foto, segunda));
    }

    @Test
    void rejeitaNenhumaOuMaisDeDuasFotosSemGastarVaga() {
        assertThatThrownBy(() -> service.transcrever(alunoId, List.of()))
                .isInstanceOfSatisfying(ImagemInvalidaException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(ImagemInvalidaException.Motivo.QUANTIDADE_INVALIDA));
        assertThatThrownBy(() -> service.transcrever(alunoId, List.of(foto, foto, foto)))
                .isInstanceOf(ImagemInvalidaException.class);

        assertThat(ia.chamadas()).isEmpty();
        assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdTranscricoes()).isZero();
    }

    @Test
    void bloqueiaAQuartaTranscricaoDoDiaSemChamarAIA() {
        for (int i = 0; i < LIMITE; i++) {
            service.transcrever(alunoId, List.of(foto));
        }

        assertThatThrownBy(() -> service.transcrever(alunoId, List.of(foto)))
                .isInstanceOfSatisfying(LimiteDiarioAtingidoException.class, e -> {
                    assertThat(e.getRecurso()).isEqualTo(RecursoIA.TRANSCRICAO);
                    assertThat(e.getLimite()).isEqualTo(LIMITE);
                });
        assertThat(ia.chamadas()).hasSize(LIMITE);
    }

    @Test
    void tetoGlobalBloqueiaSemReservarVagaNemChamarAIA() {
        var comTetoDeDois = novoServico(ia, LimitadoresDeTeste.padrao(), 2);
        comTetoDeDois.transcrever(alunoId, List.of(foto));
        comTetoDeDois.transcrever(UUID.randomUUID(), List.of(foto));

        UUID terceiro = UUID.randomUUID();
        assertThatThrownBy(() -> comTetoDeDois.transcrever(terceiro, List.of(foto)))
                .isInstanceOfSatisfying(LimiteGlobalAtingidoException.class,
                        e -> assertThat(e.getRecurso()).isEqualTo(RecursoIA.TRANSCRICAO));
        assertThat(ia.chamadas()).hasSize(2);
        assertThat(uso.usoDoDia(terceiro, HOJE_EM_SP).qtdTranscricoes()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, HOJE_EM_SP)).isEqualTo(2);
    }

    @Test
    void devolveAVagaQuandoAIAFalha() {
        ia.falharCom(new TranscricaoIAException("Gemini indisponível"));

        assertThatThrownBy(() -> service.transcrever(alunoId, List.of(foto)))
                .isInstanceOf(TranscricaoIAException.class);
        assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdTranscricoes()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, HOJE_EM_SP)).isZero();
    }

    @Test
    void devolveAVagaQuandoAFotoNaoERedacao() {
        ia.falharCom(new ImagemNaoReconhecidaException());

        assertThatThrownBy(() -> service.transcrever(alunoId, List.of(foto)))
                .isInstanceOf(ImagemNaoReconhecidaException.class);
        assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdTranscricoes()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, HOJE_EM_SP)).isZero();
    }

    // ---- Concorrência (KELLY-SEC-002) ----

    @Test
    void segundaTranscricaoDoMesmoAlunoEmAndamentoERecusadaSemGastarCota() throws Exception {
        var presa = new TranscricaoIAPresa();
        var comIAPresa = novoServico(presa, LimitadoresDeTeste.padrao(), LIMITE_GLOBAL);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ResultadoTranscricao> primeira = executor.submit(() -> comIAPresa.transcrever(alunoId, List.of(foto)));
            presa.esperarEntrada();

            assertThatThrownBy(() -> comIAPresa.transcrever(alunoId, List.of(foto)))
                    .isInstanceOfSatisfying(IAOcupadaException.class, e -> {
                        assertThat(e.getMotivo()).isEqualTo(Motivo.ALUNO_EM_ANDAMENTO);
                        assertThat(e.getRecurso()).isEqualTo(RecursoIA.TRANSCRICAO);
                    });
            assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdTranscricoes()).isEqualTo(1);
            assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, HOJE_EM_SP)).isEqualTo(1);
            assertThat(presa.chamadas).hasValue(1);

            presa.soltar(0);
            assertThat(primeira.get(10, TimeUnit.SECONDS).linhas()).isNotEmpty();
        } finally {
            presa.soltarTodas();
            executor.shutdownNow();
        }
    }

    @Test
    void comDoisAlunosTranscrevendoOTerceiroVeSistemaOcupado() throws Exception {
        var presa = new TranscricaoIAPresa();
        var comIAPresa = novoServico(presa, LimitadoresDeTeste.padrao(), LIMITE_GLOBAL); // padrão: 2 simultâneas
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // Uma de cada vez até entrar na IA: os fakes em memória não são feitos para escrita simultânea
            Future<ResultadoTranscricao> primeira = executor.submit(() -> comIAPresa.transcrever(UUID.randomUUID(), List.of(foto)));
            presa.esperarEntrada();
            Future<ResultadoTranscricao> segunda = executor.submit(() -> comIAPresa.transcrever(UUID.randomUUID(), List.of(foto)));
            presa.esperarEntrada();

            assertThatThrownBy(() -> comIAPresa.transcrever(alunoId, List.of(foto)))
                    .isInstanceOfSatisfying(IAOcupadaException.class,
                            e -> assertThat(e.getMotivo()).isEqualTo(Motivo.SISTEMA_OCUPADO));
            assertThat(uso.buscarPorUsuarioIdEData(alunoId, HOJE_EM_SP)).isEmpty();
            assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, HOJE_EM_SP)).isEqualTo(2);

            presa.soltar(0);
            primeira.get(10, TimeUnit.SECONDS);
            presa.soltar(1);
            segunda.get(10, TimeUnit.SECONDS);
        } finally {
            presa.soltarTodas();
            executor.shutdownNow();
        }
    }

    @Test
    void falhaDaIALiberaATravaParaOAlunoTranscreverDeNovo() {
        ia.falharCom(new TranscricaoIAException("Gemini indisponível"));
        assertThatThrownBy(() -> service.transcrever(alunoId, List.of(foto)))
                .isInstanceOf(TranscricaoIAException.class);
        ia.falharCom(null);

        assertThat(service.transcrever(alunoId, List.of(foto)).linhas()).isNotEmpty();
        assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, HOJE_EM_SP)).isEqualTo(1);
    }

    @Test
    void validacaoAconteceAntesDaTrava() throws Exception {
        var presa = new TranscricaoIAPresa();
        var comIAPresa = novoServico(presa, LimitadoresDeTeste.padrao(), LIMITE_GLOBAL);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<ResultadoTranscricao> primeira = executor.submit(() -> comIAPresa.transcrever(alunoId, List.of(foto)));
            presa.esperarEntrada();

            // Com a trava ocupada, 3 fotos recebem o erro de validação, não "em andamento"
            assertThatThrownBy(() -> comIAPresa.transcrever(alunoId, List.of(foto, foto, foto)))
                    .isInstanceOf(ImagemInvalidaException.class);

            presa.soltar(0);
            primeira.get(10, TimeUnit.SECONDS);
        } finally {
            presa.soltarTodas();
            executor.shutdownNow();
        }
    }

    @Test
    void corrigirNaoBloqueiaTranscrever() {
        var limitador = LimitadoresDeTeste.padrao();
        var comLimitadorCompartilhado = novoServico(ia, limitador, LIMITE_GLOBAL);

        // Uma correção do mesmo aluno em andamento, no mesmo limitador (em produção ele é um bean só)
        try (var correcao = limitador.adquirir(alunoId, RecursoIA.CORRECAO)) {
            assertThat(comLimitadorCompartilhado.transcrever(alunoId, List.of(foto)).linhas()).isNotEmpty();
        }
    }

    /**
     * IA que para dentro de cada chamada até o teste soltar aquela chamada, na ordem de chegada.
     * Simula transcrições em andamento.
     */
    private static final class TranscricaoIAPresa implements TranscricaoIAPort {
        private final Semaphore entradas = new Semaphore(0);
        private final List<CountDownLatch> saidas = new CopyOnWriteArrayList<>();
        final AtomicInteger chamadas = new AtomicInteger();

        @Override
        public ResultadoTranscricao transcrever(List<ImagemRedacao> imagens) {
            var saida = new CountDownLatch(1);
            saidas.add(saida);
            chamadas.incrementAndGet();
            entradas.release();
            try {
                if (!saida.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("IA presa por tempo demais no teste");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return new ResultadoTranscricao(List.of("Linha transcrita."), "gemini-teste", 10, 5);
        }

        void esperarEntrada() throws InterruptedException {
            assertThat(entradas.tryAcquire(5, TimeUnit.SECONDS)).as("a chamada não chegou à IA").isTrue();
        }

        void soltar(int ordemDeChegada) {
            saidas.get(ordemDeChegada).countDown();
        }

        void soltarTodas() {
            saidas.forEach(CountDownLatch::countDown);
        }
    }
}
