package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.adapter.out.persistence.repository.EventoStripeRepositoryAdapter;
import eduar.studymindredacao.domain.model.EstadoAssinaturaStripe;
import eduar.studymindredacao.domain.model.EventoWebhook;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Prova contra o Postgres real que o registro do evento e a gravação da assinatura caem juntos. NOT_SUPPORTED
 * desliga a transação do @DataJpaTest: assim a transação que vale é a do próprio serviço, como em produção.
 * Só o registro de eventos é real; as assinaturas são mock para forçar a falha. Mesmas variáveis do banco
 * dos outros testes de integração (não precisa de JWT nem de Stripe).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SincronizarAssinaturaService.class, EventoStripeRepositoryAdapter.class,
        SincronizarAssinaturaRollbackIntegrationTest.Relogio.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SincronizarAssinaturaRollbackIntegrationTest {
    private static final EstadoAssinaturaStripe ESTADO = new EstadoAssinaturaStripe("sub_rollback", "cus_rollback",
            null, CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE,
            OffsetDateTime.now(ZoneOffset.UTC).plusMonths(1), false);

    @TestConfiguration
    static class Relogio {
        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private SincronizarAssinaturaService sincronizar;
    @Autowired
    private EntityManagerFactory emf;

    @MockitoBean
    private AssinaturaRepositoryPort assinaturas;
    @MockitoBean
    private UsuarioRepositoryPort usuarios;

    private static EventoWebhook evento(String id) {
        return new EventoWebhook(id, "customer.subscription.updated", "sub_rollback");
    }

    private long registros(String eventoId) {
        var em = emf.createEntityManager();
        try {
            return ((Number) em.createNativeQuery("SELECT count(*) FROM eventos_stripe WHERE id = :id")
                    .setParameter("id", eventoId)
                    .getSingleResult()).longValue();
        } finally {
            em.close();
        }
    }

    private void apagar(String eventoId) {
        var em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            em.createNativeQuery("DELETE FROM eventos_stripe WHERE id = :id").setParameter("id", eventoId)
                    .executeUpdate();
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    private void donoConhecido() {
        when(assinaturas.buscarUsuarioPorStripeCustomerId(anyString())).thenReturn(Optional.of(UUID.randomUUID()));
        when(assinaturas.buscarPorStripeSubscriptionId(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void falhaAoGravarAAssinaturaDesfazORegistroDoEvento() {
        String id = "evt_rollback_" + UUID.randomUUID();
        donoConhecido();
        when(assinaturas.salvar(any())).thenThrow(new IllegalStateException("banco caiu no meio"));

        assertThatThrownBy(() -> sincronizar.aplicar(evento(id), ESTADO))
                .isInstanceOf(IllegalStateException.class);

        // Sem registro: o retry do Stripe vai encontrar o evento como novo e processar de novo
        assertThat(registros(id)).isZero();
    }

    @Test
    void sucessoDeixaOEventoRegistrado() {
        // Controle do teste acima: prova que a consulta enxerga o que o serviço grava de verdade
        String id = "evt_rollback_" + UUID.randomUUID();
        donoConhecido();
        when(assinaturas.salvar(any())).thenAnswer(chamada -> chamada.getArgument(0));
        try {
            assertThat(sincronizar.aplicar(evento(id), ESTADO)).isEqualTo(ResultadoWebhook.PROCESSADO);

            assertThat(registros(id)).isEqualTo(1);
        } finally {
            apagar(id);
        }
    }
}
