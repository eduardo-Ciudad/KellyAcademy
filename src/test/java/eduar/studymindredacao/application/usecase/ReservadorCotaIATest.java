package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.LimiteDiarioAtingidoException;
import eduar.studymindredacao.domain.exception.LimiteGlobalAtingidoException;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservadorCotaIATest {
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 23);
    private static final UUID ALUNO = UUID.randomUUID();
    private static final int LIMITE_ALUNO = 3;
    private static final int TETO = 30;

    private final UsoIAGlobalEmMemoria global = new UsoIAGlobalEmMemoria();

    @ParameterizedTest
    @EnumSource(RecursoIA.class)
    void reservaComFolgaContaNasDuasCotasDoProprioRecurso(RecursoIA recurso) {
        var aluno = new UsoIADiarioEmMemoria();

        new ReservadorCotaIA(global, aluno).reservar(ALUNO, recurso, HOJE, LIMITE_ALUNO, TETO);

        assertThat(global.quantidadeDoDia(recurso, HOJE)).isEqualTo(1);
        assertThat(usoDoAluno(aluno, recurso)).isEqualTo(1);
        for (RecursoIA outro : RecursoIA.values()) {
            if (outro != recurso) {
                assertThat(usoDoAluno(aluno, outro)).as(outro.name()).isZero();
                assertThat(global.quantidadeDoDia(outro, HOJE)).as(outro.name()).isZero();
            }
        }
    }

    @Test
    void tetoGlobalCheioRecusaSemTocarNaCotaDoAluno() {
        var aluno = new UsoIADiarioEmMemoria();
        var reservador = new ReservadorCotaIA(global, aluno);
        global.reservar(RecursoIA.CORRECAO, HOJE, 1);

        assertThatThrownBy(() -> reservador.reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, 1))
                .isInstanceOfSatisfying(LimiteGlobalAtingidoException.class,
                        e -> assertThat(e.getRecurso()).isEqualTo(RecursoIA.CORRECAO));

        assertThat(usoDoAluno(aluno, RecursoIA.CORRECAO)).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE)).isEqualTo(1);
    }

    @Test
    void limiteDoAlunoCheioRecusaEDevolveAVagaGlobal() {
        var aluno = new UsoIADiarioEmMemoria();
        var reservador = new ReservadorCotaIA(global, aluno);
        for (int i = 0; i < LIMITE_ALUNO; i++) {
            reservador.reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO);
        }

        assertThatThrownBy(() -> reservador.reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO))
                .isInstanceOfSatisfying(LimiteDiarioAtingidoException.class, e -> {
                    assertThat(e.getRecurso()).isEqualTo(RecursoIA.CORRECAO);
                    assertThat(e.getLimite()).isEqualTo(LIMITE_ALUNO);
                });

        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE)).isEqualTo(LIMITE_ALUNO);
        assertThat(usoDoAluno(aluno, RecursoIA.CORRECAO)).isEqualTo(LIMITE_ALUNO);
    }

    @ParameterizedTest
    @EnumSource(RecursoIA.class)
    void liberarDevolveAsDuasCotas(RecursoIA recurso) {
        var aluno = new UsoIADiarioEmMemoria();
        var reserva = new ReservadorCotaIA(global, aluno).reservar(ALUNO, recurso, HOJE, LIMITE_ALUNO, TETO);

        reserva.liberar();

        assertThat(global.quantidadeDoDia(recurso, HOJE)).isZero();
        assertThat(usoDoAluno(aluno, recurso)).isZero();
    }

    @Test
    void liberarDuasVezesDevolveSoUmaVez() {
        var aluno = new UsoIADiarioEmMemoria();
        var reservador = new ReservadorCotaIA(global, aluno);
        reservador.reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO); // correção anterior, já usada
        var reserva = reservador.reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO);

        reserva.liberar();
        reserva.liberar();

        // Se devolvesse duas vezes, cairia para 0: o aluno ganharia uma correção grátis
        assertThat(usoDoAluno(aluno, RecursoIA.CORRECAO)).isEqualTo(1);
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE)).isEqualTo(1);
    }

    @Test
    void duasThreadsLiberandoAoMesmoTempoDevolvemSoUmaVez() throws Exception {
        var devolucoes = new AtomicInteger();
        var aluno = new UsoIADiarioEmMemoria() {
            @Override
            public void liberarCorrecao(UUID usuarioId, LocalDate data) {
                devolucoes.incrementAndGet();
                super.liberarCorrecao(usuarioId, data);
            }
        };
        var reserva = new ReservadorCotaIA(global, aluno).reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO);

        ExecutorService executor = Executors.newFixedThreadPool(8);
        var largada = new CountDownLatch(1);
        try {
            for (int i = 0; i < 8; i++) {
                executor.submit(() -> {
                    largada.await();
                    reserva.liberar();
                    return null;
                });
            }
            largada.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdownNow();
        }

        assertThat(devolucoes).hasValue(1);
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE)).isZero();
    }

    @Test
    void bancoFalhandoNaReservaDoAlunoDevolveAGlobalESobeOErroOriginal() {
        var falha = new IllegalStateException("banco fora");
        var aluno = new UsoIADiarioEmMemoria() {
            @Override
            public boolean reservarCorrecao(UUID usuarioId, LocalDate data, int limite) {
                throw falha;
            }
        };

        assertThatThrownBy(() -> new ReservadorCotaIA(global, aluno)
                .reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO))
                .isSameAs(falha);

        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE)).isZero();
    }

    @Test
    void falhaAoDevolverAGlobalNaoEscondeOLimiteDoAluno() {
        var aluno = new UsoIADiarioEmMemoria();
        global.falharAoLiberar();

        assertThatThrownBy(() -> new ReservadorCotaIA(global, aluno)
                .reservar(ALUNO, RecursoIA.CORRECAO, HOJE, 0, TETO))
                .isInstanceOf(LimiteDiarioAtingidoException.class)
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1));
    }

    @Test
    void falhaAoDevolverACotaDoAlunoAindaDevolveAGlobalENaoLanca() {
        var aluno = new UsoIADiarioEmMemoria() {
            @Override
            public void liberarCorrecao(UUID usuarioId, LocalDate data) {
                throw new IllegalStateException("banco fora");
            }
        };
        var reserva = new ReservadorCotaIA(global, aluno).reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO);

        assertThatCode(reserva::liberar).doesNotThrowAnyException();

        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, HOJE)).isZero();
    }

    @Test
    void falhaAoDevolverAGlobalNoLiberarNaoLanca() {
        var aluno = new UsoIADiarioEmMemoria();
        var reserva = new ReservadorCotaIA(global, aluno).reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO);
        global.falharAoLiberar();

        assertThatCode(reserva::liberar).doesNotThrowAnyException();

        assertThat(usoDoAluno(aluno, RecursoIA.CORRECAO)).isZero();
    }

    @Test
    void liberarDevolveNoDiaDaReservaEnaoNoDiaSeguinte() {
        var aluno = new UsoIADiarioEmMemoria();
        var reservador = new ReservadorCotaIA(global, aluno);
        var reservaDeHoje = reservador.reservar(ALUNO, RecursoIA.CORRECAO, HOJE, LIMITE_ALUNO, TETO);
        var amanha = HOJE.plusDays(1);
        reservador.reservar(ALUNO, RecursoIA.CORRECAO, amanha, LIMITE_ALUNO, TETO);

        // Ex.: correção enviada às 23:59:50 que falha às 00:00:20
        reservaDeHoje.liberar();

        assertThat(reservaDeHoje.dia()).isEqualTo(HOJE);
        assertThat(usoDoAluno(aluno, RecursoIA.CORRECAO)).isZero();
        assertThat(aluno.usoDoDia(ALUNO, amanha).qtdCorrecoes()).isEqualTo(1);
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, amanha)).isEqualTo(1);
    }

    @Test
    void tetoGlobalZeradoRecusaTudo() {
        var aluno = new UsoIADiarioEmMemoria();

        assertThatThrownBy(() -> new ReservadorCotaIA(global, aluno)
                .reservar(ALUNO, RecursoIA.JULIA, HOJE, LIMITE_ALUNO, 0))
                .isInstanceOf(LimiteGlobalAtingidoException.class);

        assertThat(usoDoAluno(aluno, RecursoIA.JULIA)).isZero();
    }

    @Test
    void limiteDoAlunoZeradoRecusaSemDeixarVagaGlobalPresa() {
        var aluno = new UsoIADiarioEmMemoria();

        assertThatThrownBy(() -> new ReservadorCotaIA(global, aluno)
                .reservar(ALUNO, RecursoIA.TRANSCRICAO, HOJE, 0, TETO))
                .isInstanceOf(LimiteDiarioAtingidoException.class);

        assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, HOJE)).isZero();
    }

    private static int usoDoAluno(UsoIADiarioEmMemoria aluno, RecursoIA recurso) {
        return switch (recurso) {
            case CORRECAO -> aluno.usoDoDia(ALUNO, HOJE).qtdCorrecoes();
            case TRANSCRICAO -> aluno.usoDoDia(ALUNO, HOJE).qtdTranscricoes();
            case JULIA -> aluno.contarMensagensJulia(ALUNO, HOJE);
        };
    }
}
