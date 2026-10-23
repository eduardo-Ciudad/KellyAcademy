package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.IAOcupadaException;
import eduar.studymindredacao.domain.exception.IAOcupadaException.Motivo;
import eduar.studymindredacao.domain.model.enums.RecursoIA;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitadorConcorrenciaIATest {
    private static final UUID ALUNO = UUID.randomUUID();

    private static LimitadorConcorrenciaIA limitador(int correcao, int transcricao, int julia) {
        Map<RecursoIA, Integer> maximos = new EnumMap<>(RecursoIA.class);
        maximos.put(RecursoIA.CORRECAO, correcao);
        maximos.put(RecursoIA.TRANSCRICAO, transcricao);
        maximos.put(RecursoIA.JULIA, julia);
        return new LimitadorConcorrenciaIA(maximos);
    }

    @Test
    void mesmoAlunoComChamadaEmAndamentoERecusado() {
        var limitador = limitador(4, 2, 4);
        limitador.adquirir(ALUNO, RecursoIA.CORRECAO);

        assertThatThrownBy(() -> limitador.adquirir(ALUNO, RecursoIA.CORRECAO))
                .isInstanceOfSatisfying(IAOcupadaException.class, e -> {
                    assertThat(e.getMotivo()).isEqualTo(Motivo.ALUNO_EM_ANDAMENTO);
                    assertThat(e.getRecurso()).isEqualTo(RecursoIA.CORRECAO);
                });
    }

    @Test
    void depoisDoCloseOMesmoAlunoConsegueDeNovo() {
        var limitador = limitador(4, 2, 4);
        limitador.adquirir(ALUNO, RecursoIA.CORRECAO).close();

        try (var permissao = limitador.adquirir(ALUNO, RecursoIA.CORRECAO)) {
            assertThat(permissao).isNotNull();
        }
    }

    @Test
    void alunosDiferentesPassamAteOLimiteEOSeguinteVeSistemaOcupado() {
        var limitador = limitador(2, 2, 4);
        limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO);
        limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO);

        assertThatThrownBy(() -> limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO))
                .isInstanceOfSatisfying(IAOcupadaException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Motivo.SISTEMA_OCUPADO));
    }

    @Test
    void alunoRecusadoPorSistemaCheioNaoFicaMarcadoComoEmAndamento() {
        var limitador = limitador(1, 2, 4);
        var outro = limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO);
        assertThatThrownBy(() -> limitador.adquirir(ALUNO, RecursoIA.CORRECAO))
                .isInstanceOfSatisfying(IAOcupadaException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Motivo.SISTEMA_OCUPADO));

        outro.close();

        // Se tivesse ficado marcado, aqui viria ALUNO_EM_ANDAMENTO mesmo com o sistema livre
        try (var permissao = limitador.adquirir(ALUNO, RecursoIA.CORRECAO)) {
            assertThat(permissao).isNotNull();
        }
    }

    @Test
    void recursosSaoIndependentes() {
        var limitador = limitador(1, 1, 1);
        limitador.adquirir(ALUNO, RecursoIA.CORRECAO);

        try (var julia = limitador.adquirir(ALUNO, RecursoIA.JULIA);
             var transcricao = limitador.adquirir(ALUNO, RecursoIA.TRANSCRICAO)) {
            assertThat(julia).isNotNull();
            assertThat(transcricao).isNotNull();
        }
    }

    @Test
    void closeRepetidoNaoAumentaOLimite() {
        var limitador = limitador(2, 2, 4);
        var primeira = limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO);
        primeira.close();
        primeira.close();
        primeira.close();

        limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO);
        limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO);

        // Se cada close devolvesse uma vaga, o limite teria virado 4 e esta passaria
        assertThatThrownBy(() -> limitador.adquirir(UUID.randomUUID(), RecursoIA.CORRECAO))
                .isInstanceOfSatisfying(IAOcupadaException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Motivo.SISTEMA_OCUPADO));
    }

    @Test
    void tryWithResourcesLiberaMesmoQuandoOBlocoLanca() {
        var limitador = limitador(1, 2, 4);

        assertThatThrownBy(() -> {
            try (var permissao = limitador.adquirir(ALUNO, RecursoIA.CORRECAO)) {
                throw new IllegalStateException("IA falhou");
            }
        }).hasMessage("IA falhou");

        try (var permissao = limitador.adquirir(ALUNO, RecursoIA.CORRECAO)) {
            assertThat(permissao).isNotNull();
        }
    }

    @Test
    void dezRequisicoesSimultaneasDoMesmoAlunoSoUmaPassa() throws Exception {
        var limitador = limitador(10, 2, 4);

        List<Boolean> resultados = disputarAoMesmoTempo(10, () -> tentar(limitador, ALUNO));

        assertThat(resultados).filteredOn(Boolean::booleanValue).hasSize(1);
    }

    @Test
    void dezAlunosDiferentesAoMesmoTempoContraLimiteQuatroSoQuatroPassam() throws Exception {
        var limitador = limitador(4, 2, 4);

        List<Boolean> resultados = disputarAoMesmoTempo(10, () -> tentar(limitador, UUID.randomUUID()));

        assertThat(resultados).filteredOn(Boolean::booleanValue).hasSize(4);
    }

    @Test
    void limiteZeroOuNegativoDerrubaASubida() {
        assertThatThrownBy(() -> limitador(0, 2, 4)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CORRECAO");
        assertThatThrownBy(() -> limitador(4, -1, 4)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TRANSCRICAO");
    }

    @Test
    void recursoSemLimiteConfiguradoDerrubaASubida() {
        Map<RecursoIA, Integer> semJulia = new EnumMap<>(RecursoIA.class);
        semJulia.put(RecursoIA.CORRECAO, 4);
        semJulia.put(RecursoIA.TRANSCRICAO, 2);

        assertThatThrownBy(() -> new LimitadorConcorrenciaIA(semJulia))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JULIA");
    }

    /** Fica com a permissão (não fecha): simula chamadas que ainda estão em andamento. */
    private static boolean tentar(LimitadorConcorrenciaIA limitador, UUID aluno) {
        try {
            limitador.adquirir(aluno, RecursoIA.CORRECAO);
            return true;
        } catch (IAOcupadaException e) {
            return false;
        }
    }

    private static List<Boolean> disputarAoMesmoTempo(int threads, Callable<Boolean> tentativa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futuros = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return tentativa.call();
                }));
            }
            largada.countDown();
            List<Boolean> resultados = new ArrayList<>();
            for (Future<Boolean> futuro : futuros) {
                resultados.add(futuro.get(10, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }
}
