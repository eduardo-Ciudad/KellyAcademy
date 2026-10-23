package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.AvaliacaoIAException;
import eduar.studymindredacao.domain.exception.IAOcupadaException;
import eduar.studymindredacao.domain.exception.IAOcupadaException.Motivo;
import eduar.studymindredacao.domain.exception.LimiteDiarioAtingidoException;
import eduar.studymindredacao.domain.exception.LimiteGlobalAtingidoException;
import eduar.studymindredacao.domain.exception.TemaNaoEncontradoException;
import eduar.studymindredacao.domain.model.CompetenciaAvaliada;
import eduar.studymindredacao.domain.model.EvidenciaCompetencia;
import eduar.studymindredacao.domain.model.Redacao;
import eduar.studymindredacao.domain.model.ResultadoAvaliacaoIA;
import eduar.studymindredacao.domain.model.SolicitacaoAvaliacao;
import eduar.studymindredacao.domain.model.Tema;
import eduar.studymindredacao.domain.model.enums.OrigemRedacao;
import eduar.studymindredacao.domain.model.enums.OrigemTema;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.model.enums.StatusRedacao;
import eduar.studymindredacao.domain.model.enums.TipoRedacao;
import eduar.studymindredacao.domain.port.AvaliacaoIAPort;
import eduar.studymindredacao.domain.port.RedacaoRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
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

class EnviarRedacaoServiceTest {
    private static final int LIMITE = 3;
    private static final int LIMITE_GLOBAL = 30;
    // 01:30 UTC do dia 29 = 22:30 do dia 28 em São Paulo
    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-09-29T01:30:00Z"), ZoneId.of("America/Sao_Paulo"));
    private static final LocalDate HOJE_EM_SP = LocalDate.of(2026, 9, 28);
    private static final String TEXTO = "Texto da redação do aluno sobre o tema proposto.";

    private final UUID alunoId = UUID.randomUUID();
    private final TemaRepositoryEmMemoria temas = new TemaRepositoryEmMemoria();
    private final RedacaoRepositoryEmMemoria redacoes = new RedacaoRepositoryEmMemoria();
    private final AvaliacaoRepositoryEmMemoria avaliacoes = new AvaliacaoRepositoryEmMemoria();
    private final UsoIADiarioEmMemoria uso = new UsoIADiarioEmMemoria();
    private final UsoIAGlobalEmMemoria global = new UsoIAGlobalEmMemoria();
    private final List<SolicitacaoAvaliacao> chamadasIA = new ArrayList<>();
    private RuntimeException falhaDaIA;
    private ResultadoAvaliacaoIA respostaDaIA;
    private EnviarRedacaoService service;
    private Tema tema;

    private final AvaliacaoIAPort iaFake = solicitacao -> {
        chamadasIA.add(solicitacao);
        if (falhaDaIA != null) {
            throw falhaDaIA;
        }
        return respostaDaIA != null ? respostaDaIA : resultado();
    };

    private static ResultadoAvaliacaoIA resultado() {
        var competencias = List.of(
                new CompetenciaAvaliada(1, 160, "n", "r1", List.of()),
                new CompetenciaAvaliada(2, 120, "n", "r2", List.of()),
                new CompetenciaAvaliada(3, 120, "n", "r3", List.of()),
                new CompetenciaAvaliada(4, 160, "n", "r4", List.of()),
                new CompetenciaAvaliada(5, 80, "n", "r5", List.of("sem agente"))
        );
        return new ResultadoAvaliacaoIA(false, null, competencias, List.of("tese clara"), List.of("proposta"),
                "Fragilidade na C5.", "gemini-teste", 2000, 600, "{}");
    }

    @BeforeEach
    void setUp() {
        tema = temas.salvar(new Tema(null, "Democratização do acesso ao cinema no Brasil", null, OrigemTema.ENEM_OFICIAL, (short) 2019, true, null));
        service = novoServico(redacoes, iaFake, LimitadoresDeTeste.padrao(), LIMITE_GLOBAL);
    }

    private EnviarRedacaoService novoServico(RedacaoRepositoryPort repositorio, AvaliacaoIAPort ia,
                                             LimitadorConcorrenciaIA limitador, int tetoGlobal) {
        var concluir = new ConcluirAvaliacaoService(avaliacoes, repositorio, uso);
        return new EnviarRedacaoService(temas, repositorio, ia, concluir, new ReservadorCotaIA(global, uso),
                limitador, RELOGIO, LIMITE, tetoGlobal);
    }

    @Test
    void corrigeRedacaoESalvaTudo() {
        var detalhada = service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);

        assertThat(detalhada.redacao().status()).isEqualTo(StatusRedacao.AVALIADA);
        assertThat(detalhada.avaliacao().notaTotal()).isEqualTo((short) 640);
        assertThat(detalhada.avaliacao().redacaoId()).isEqualTo(detalhada.redacao().id());
        assertThat(detalhada.tema().titulo()).isEqualTo(tema.titulo());
        assertThat(avaliacoes.todas()).hasSize(1);
        assertThat(chamadasIA).singleElement()
                .satisfies(s -> assertThat(s.tema()).isEqualTo(tema.titulo()));

        var usoDoDia = uso.usoDoDia(alunoId, HOJE_EM_SP);
        assertThat(usoDoDia.qtdCorrecoes()).isEqualTo(1);
        assertThat(usoDoDia.tokensEntrada()).isEqualTo(2000);
        assertThat(usoDoDia.tokensSaida()).isEqualTo(600);
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE_EM_SP)).isEqualTo(1);
    }

    @Test
    void guardaAOrigemManuscrita() {
        var detalhada = service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.MANUSCRITO);

        assertThat(detalhada.redacao().origem()).isEqualTo(OrigemRedacao.MANUSCRITO);
        assertThat(redacoes.todas()).singleElement()
                .satisfies(r -> assertThat(r.origem()).isEqualTo(OrigemRedacao.MANUSCRITO));
    }

    @Test
    void semOrigemInformadaContaComoDigitada() {
        var detalhada = service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, null);

        assertThat(detalhada.redacao().origem()).isEqualTo(OrigemRedacao.DIGITADO);
    }

    @Test
    void contaOLimiteNoDiaDeSaoPauloENaoEmUtc() {
        service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);

        assertThat(uso.buscarPorUsuarioIdEData(alunoId, HOJE_EM_SP)).isPresent();
        assertThat(uso.buscarPorUsuarioIdEData(alunoId, HOJE_EM_SP.plusDays(1))).isEmpty();
    }

    @Test
    void bloqueiaQuartaCorrecaoDoDiaSemChamarAIA() {
        for (int i = 0; i < LIMITE; i++) {
            service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);
        }

        assertThatThrownBy(() -> service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                .isInstanceOf(LimiteDiarioAtingidoException.class);
        assertThat(chamadasIA).hasSize(LIMITE);
        assertThat(redacoes.todas()).hasSize(LIMITE);
    }

    @Test
    void notaQueAGradeNaoSustentaELimitadaAntesDeSalvar() {
        var tresRepeticoes = new EvidenciaCompetencia(null, null, null, null, 3, 0, 3, null);
        var competencias = List.of(
                new CompetenciaAvaliada(1, 200, "n", "r1", List.of()),
                new CompetenciaAvaliada(2, 200, "n", "r2", List.of()),
                new CompetenciaAvaliada(3, 200, "n", "r3", List.of()),
                new CompetenciaAvaliada(4, 200, "n", "r4", List.of("a", "b", "c"), tresRepeticoes),
                new CompetenciaAvaliada(5, 200, "n", "r5", List.of())
        );
        respostaDaIA = new ResultadoAvaliacaoIA(false, null, competencias, List.of(), List.of(),
                "d", "gemini-teste", 2000, 600, "{}");

        var detalhada = service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);

        assertThat(detalhada.avaliacao().notaTotal()).isEqualTo((short) 960);
        assertThat(avaliacoes.todas()).singleElement()
                .satisfies(a -> assertThat(a.competencias().get(3).problemasIdentificados())
                        .last().asString().startsWith("Nota limitada a 160 pela grade do INEP"));
    }

    @Test
    void falhaDaIAMarcaErroEDevolveAVaga() {
        falhaDaIA = new AvaliacaoIAException("Gemini indisponível");

        assertThatThrownBy(() -> service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                .isInstanceOf(AvaliacaoIAException.class);

        assertThat(redacoes.todas()).singleElement()
                .satisfies(r -> assertThat(r.status()).isEqualTo(StatusRedacao.ERRO));
        assertThat(avaliacoes.todas()).isEmpty();
        assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdCorrecoes()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE_EM_SP)).isZero();
    }

    @Test
    void depoisDeUmaFalhaOAlunoAindaTemAsTresVagas() {
        falhaDaIA = new AvaliacaoIAException("timeout");
        assertThatThrownBy(() -> service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO));
        falhaDaIA = null;

        for (int i = 0; i < LIMITE; i++) {
            service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);
        }
        assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdCorrecoes()).isEqualTo(LIMITE);
    }

    @Test
    void rejeitaTemaInexistenteSemGastarVaga() {
        assertThatThrownBy(() -> service.enviar(alunoId, UUID.randomUUID(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                .isInstanceOf(TemaNaoEncontradoException.class);
        assertThat(uso.buscarPorUsuarioIdEData(alunoId, HOJE_EM_SP)).isEmpty();
    }

    @Test
    void rejeitaTemaInativo() {
        var inativo = temas.salvar(new Tema(null, "Tema antigo", null, OrigemTema.AUTORAL, null, false, null));

        assertThatThrownBy(() -> service.enviar(alunoId, inativo.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                .isInstanceOf(TemaNaoEncontradoException.class);
    }

    @Test
    void rejeitaTextoAcimaDoLimiteSemGastarVaga() {
        String longo = "a".repeat(EnviarRedacaoService.TAMANHO_MAXIMO_TEXTO + 1);

        assertThatThrownBy(() -> service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, longo, OrigemRedacao.DIGITADO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5000");
        assertThat(uso.buscarPorUsuarioIdEData(alunoId, HOJE_EM_SP)).isEmpty();
        assertThat(chamadasIA).isEmpty();
    }

    @Test
    void rejeitaTextoVazio() {
        assertThatThrownBy(() -> service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, "   ", OrigemRedacao.DIGITADO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tetoGlobalBloqueiaSemReservarVagaNemChamarAIA() {
        var comTetoDeDois = novoServico(redacoes, iaFake, LimitadoresDeTeste.padrao(), 2);
        UUID outroAluno = UUID.randomUUID();
        comTetoDeDois.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);
        comTetoDeDois.enviar(outroAluno, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);

        UUID terceiro = UUID.randomUUID();
        assertThatThrownBy(() -> comTetoDeDois.enviar(terceiro, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                .isInstanceOf(LimiteGlobalAtingidoException.class);

        assertThat(chamadasIA).hasSize(2);
        assertThat(uso.usoDoDia(terceiro, HOJE_EM_SP).qtdCorrecoes()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE_EM_SP)).isEqualTo(2);
    }

    // ---- Concorrência (KELLY-SEC-002) ----

    @Test
    void segundoEnvioDoMesmoAlunoEmAndamentoERecusadoSemGastarCota() throws Exception {
        var ia = new IAPresa();
        var comIAPresa = novoServico(redacoes, ia, LimitadoresDeTeste.padrao(), LIMITE_GLOBAL);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RedacaoDetalhada> primeira = executor.submit(() ->
                    comIAPresa.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO));
            assertThat(ia.dentro.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> comIAPresa.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                    .isInstanceOfSatisfying(IAOcupadaException.class, e -> {
                        assertThat(e.getMotivo()).isEqualTo(Motivo.ALUNO_EM_ANDAMENTO);
                        assertThat(e.getRecurso()).isEqualTo(RecursoIA.CORRECAO);
                    });
            // Só a primeira gastou vaga, chamou a IA e criou redação
            assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdCorrecoes()).isEqualTo(1);
            assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE_EM_SP)).isEqualTo(1);
            assertThat(ia.chamadas).hasValue(1);
            assertThat(redacoes.todas()).hasSize(1);

            ia.soltar.countDown();
            assertThat(primeira.get(10, TimeUnit.SECONDS).redacao().status()).isEqualTo(StatusRedacao.AVALIADA);
        } finally {
            ia.soltar.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void sistemaCheioRecusaSemGastarCotaNemSalvarRedacao() throws Exception {
        var ia = new IAPresa();
        var comUmaVaga = novoServico(redacoes, ia, LimitadoresDeTeste.com(RecursoIA.CORRECAO, 1), LIMITE_GLOBAL);
        UUID outroAluno = UUID.randomUUID();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RedacaoDetalhada> doOutro = executor.submit(() ->
                    comUmaVaga.enviar(outroAluno, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO));
            assertThat(ia.dentro.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> comUmaVaga.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                    .isInstanceOfSatisfying(IAOcupadaException.class,
                            e -> assertThat(e.getMotivo()).isEqualTo(Motivo.SISTEMA_OCUPADO));
            assertThat(uso.buscarPorUsuarioIdEData(alunoId, HOJE_EM_SP)).isEmpty();
            assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE_EM_SP)).isEqualTo(1);
            assertThat(redacoes.todas()).hasSize(1);

            ia.soltar.countDown();
            doOutro.get(10, TimeUnit.SECONDS);
        } finally {
            ia.soltar.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void falhaDaIALiberaATravaParaOAlunoEnviarDeNovo() {
        falhaDaIA = new AvaliacaoIAException("Gemini indisponível");
        assertThatThrownBy(() -> service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                .isInstanceOf(AvaliacaoIAException.class);
        falhaDaIA = null;

        var detalhada = service.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);

        assertThat(detalhada.redacao().status()).isEqualTo(StatusRedacao.AVALIADA);
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE_EM_SP)).isEqualTo(1);
    }

    @Test
    void falhaAoSalvarARedacaoDevolveAsDuasVagasESoltaATrava() {
        var falhaUmaVez = new RedacaoRepositoryEmMemoria() {
            private boolean jaFalhou;

            @Override
            public Redacao salvar(Redacao redacao) {
                if (!jaFalhou) {
                    jaFalhou = true;
                    throw new IllegalStateException("banco fora");
                }
                return super.salvar(redacao);
            }
        };
        var comBancoInstavel = novoServico(falhaUmaVez, iaFake, LimitadoresDeTeste.padrao(), LIMITE_GLOBAL);

        assertThatThrownBy(() -> comBancoInstavel.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO))
                .hasMessage("banco fora");

        assertThat(uso.usoDoDia(alunoId, HOJE_EM_SP).qtdCorrecoes()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE_EM_SP)).isZero();
        assertThat(chamadasIA).isEmpty();

        var detalhada = comBancoInstavel.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO);
        assertThat(detalhada.redacao().status()).isEqualTo(StatusRedacao.AVALIADA);
    }

    @Test
    void validacaoAconteceAntesDaTrava() throws Exception {
        var ia = new IAPresa();
        var comIAPresa = novoServico(redacoes, ia, LimitadoresDeTeste.padrao(), LIMITE_GLOBAL);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<RedacaoDetalhada> primeira = executor.submit(() ->
                    comIAPresa.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, TEXTO, OrigemRedacao.DIGITADO));
            assertThat(ia.dentro.await(5, TimeUnit.SECONDS)).isTrue();

            // Com a trava ocupada, um texto inválido recebe o erro de validação, não "em andamento"
            assertThatThrownBy(() -> comIAPresa.enviar(alunoId, tema.id(), TipoRedacao.PRATICA, "   ", OrigemRedacao.DIGITADO))
                    .isInstanceOf(IllegalArgumentException.class);

            ia.soltar.countDown();
            primeira.get(10, TimeUnit.SECONDS);
        } finally {
            ia.soltar.countDown();
            executor.shutdownNow();
        }
    }

    /** IA que para dentro da chamada até o teste soltar: simula uma correção em andamento. */
    private static final class IAPresa implements AvaliacaoIAPort {
        final CountDownLatch dentro = new CountDownLatch(1);
        final CountDownLatch soltar = new CountDownLatch(1);
        final AtomicInteger chamadas = new AtomicInteger();

        @Override
        public ResultadoAvaliacaoIA avaliar(SolicitacaoAvaliacao solicitacao) {
            chamadas.incrementAndGet();
            dentro.countDown();
            try {
                if (!soltar.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("IA presa por tempo demais no teste");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return resultado();
        }
    }
}
