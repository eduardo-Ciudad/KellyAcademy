package eduar.studymindredacao.adapter.out.persistence.repository;

import eduar.studymindredacao.domain.model.enums.RecursoIA;
import eduar.studymindredacao.domain.port.UsoIAGlobalRepositoryPort;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teto global (V24) direto no Postgres, com requisições concorrentes de verdade (KELLY-SEC-002).
 * NOT_SUPPORTED desliga a transação do @DataJpaTest: cada reserva faz commit na própria transação, como em
 * produção. Com a transação do teste, as threads não disputariam a mesma linha e o teste passaria mesmo
 * com a query errada. Cada teste usa uma data aleatória bem no futuro e apaga as linhas dela no fim,
 * para poder rodar várias vezes no banco local sem resíduo.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UsoIAGlobalRepositoryAdapter.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UsoIAGlobalIntegrationTest {

    @Autowired
    private UsoIAGlobalRepositoryPort global;
    @Autowired
    private EntityManagerFactory emf;

    private LocalDate dia;

    @BeforeEach
    void sortearDia() {
        var random = ThreadLocalRandom.current();
        dia = LocalDate.of(random.nextInt(2100, 3000), 1, 1).plusDays(random.nextInt(0, 365));
    }

    @AfterEach
    void apagarLinhasDoTeste() {
        var em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createNativeQuery("DELETE FROM uso_ia_global_diario WHERE data IN (:dia, :seguinte)")
                    .setParameter("dia", dia)
                    .setParameter("seguinte", dia.plusDays(1))
                    .executeUpdate();
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    @Test
    void dezRequisicoesSimultaneasComUmaVagaSoUmaReserva() throws Exception {
        List<Boolean> resultados = disputarAoMesmoTempo(10, () -> global.reservar(RecursoIA.CORRECAO, dia, 1));

        assertThat(resultados).filteredOn(Boolean::booleanValue).hasSize(1);
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, dia)).isEqualTo(1);
    }

    @Test
    void vinteRequisicoesSimultaneasComCincoVagasSoCincoReservam() throws Exception {
        List<Boolean> resultados = disputarAoMesmoTempo(20, () -> global.reservar(RecursoIA.CORRECAO, dia, 5));

        assertThat(resultados).filteredOn(Boolean::booleanValue).hasSize(5);
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, dia)).isEqualTo(5);
    }

    @Test
    void reservaAteOTetoERecusaASeguinte() {
        for (int i = 0; i < 3; i++) {
            assertThat(global.reservar(RecursoIA.TRANSCRICAO, dia, 3)).isTrue();
        }

        assertThat(global.reservar(RecursoIA.TRANSCRICAO, dia, 3)).isFalse();
        assertThat(global.quantidadeDoDia(RecursoIA.TRANSCRICAO, dia)).isEqualTo(3);
    }

    @Test
    void liberarDevolveUmENuncaFicaNegativo() {
        global.reservar(RecursoIA.JULIA, dia, 10);

        global.liberar(RecursoIA.JULIA, dia);
        global.liberar(RecursoIA.JULIA, dia);

        assertThat(global.quantidadeDoDia(RecursoIA.JULIA, dia)).isZero();
    }

    @Test
    void liberarSemLinhaNoDiaNaoCriaLinhaNemFalha() {
        global.liberar(RecursoIA.CORRECAO, dia);

        assertThat(linhasDoDia()).isZero();
        assertThat(global.quantidadeDoDia(RecursoIA.CORRECAO, dia)).isZero();
    }

    @Test
    void recursosNaoSeMisturam() {
        global.reservar(RecursoIA.CORRECAO, dia, 1);

        assertThat(global.reservar(RecursoIA.CORRECAO, dia, 1)).isFalse();
        assertThat(global.reservar(RecursoIA.TRANSCRICAO, dia, 1)).isTrue();
        assertThat(global.reservar(RecursoIA.JULIA, dia, 1)).isTrue();
    }

    @Test
    void diasNaoSeMisturam() {
        global.reservar(RecursoIA.CORRECAO, dia, 1);

        assertThat(global.reservar(RecursoIA.CORRECAO, dia, 1)).isFalse();
        assertThat(global.reservar(RecursoIA.CORRECAO, dia.plusDays(1), 1)).isTrue();
    }

    @Test
    void tetoZeroRecusaSemCriarLinha() {
        assertThat(global.reservar(RecursoIA.CORRECAO, dia, 0)).isFalse();
        assertThat(linhasDoDia()).isZero();
    }

    /** Solta todas as threads juntas num CountDownLatch, para as reservas realmente disputarem a mesma linha. */
    private static List<Boolean> disputarAoMesmoTempo(int threads, Callable<Boolean> reserva) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futuros = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return reserva.call();
                }));
            }
            largada.countDown();
            List<Boolean> resultados = new ArrayList<>();
            for (Future<Boolean> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }

    private long linhasDoDia() {
        var em = emf.createEntityManager();
        try {
            return ((Number) em.createNativeQuery("SELECT count(*) FROM uso_ia_global_diario WHERE data = :dia")
                    .setParameter("dia", dia)
                    .getSingleResult()).longValue();
        } finally {
            em.close();
        }
    }
}
