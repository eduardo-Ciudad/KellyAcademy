package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.EstadoAssinaturaStripe;
import eduar.studymindredacao.domain.model.EventoWebhook;
import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.Role;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.EventoStripeRepositoryPort;
import eduar.studymindredacao.domain.port.UsuarioRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SincronizarAssinaturaServiceTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 20, 19, 0, 0, 0, ZoneOffset.UTC);
    private static final UUID DONO = UUID.randomUUID();
    private static final EventoWebhook EVENTO =
            new EventoWebhook("evt_1", "customer.subscription.updated", "sub_1");

    @Mock
    private EventoStripeRepositoryPort eventos;
    @Mock
    private AssinaturaRepositoryPort assinaturas;
    @Mock
    private UsuarioRepositoryPort usuarios;

    private SincronizarAssinaturaService service;

    @BeforeEach
    void criar() {
        service = new SincronizarAssinaturaService(eventos, assinaturas, usuarios,
                Clock.fixed(AGORA.toInstant(), ZoneOffset.UTC));
    }

    private static EstadoAssinaturaStripe estado(StatusAssinatura status, UUID usuarioMetadata) {
        return new EstadoAssinaturaStripe("sub_1", "cus_1", usuarioMetadata, CicloAssinatura.MENSAL, status,
                AGORA.plusMonths(1), false);
    }

    private void eventoNovo() {
        when(eventos.registrarSeNovo(eq("evt_1"), eq("customer.subscription.updated"), any())).thenReturn(true);
    }

    private Assinatura gravada() {
        var captor = ArgumentCaptor.forClass(Assinatura.class);
        verify(assinaturas).salvar(captor.capture());
        return captor.getValue();
    }

    @Test
    void eventoNovoEAssinaturaNovaGravaComODonoDoCliente() {
        eventoNovo();
        when(assinaturas.buscarUsuarioPorStripeCustomerId("cus_1")).thenReturn(Optional.of(DONO));
        when(assinaturas.buscarPorStripeSubscriptionId("sub_1")).thenReturn(Optional.empty());

        var resultado = service.aplicar(EVENTO, estado(StatusAssinatura.ACTIVE, null));

        assertThat(resultado).isEqualTo(ResultadoWebhook.PROCESSADO);
        var nova = gravada();
        assertThat(nova.id()).isNull();
        assertThat(nova.usuarioId()).isEqualTo(DONO);
        assertThat(nova.stripeSubscriptionId()).isEqualTo("sub_1");
        assertThat(nova.status()).isEqualTo(StatusAssinatura.ACTIVE);
        assertThat(nova.periodoAtualFim()).isEqualTo(AGORA.plusMonths(1));
        assertThat(nova.criadoEm()).isEqualTo(AGORA);
    }

    @Test
    void assinaturaExistenteEAtualizadaMantendoAIdentidade() {
        eventoNovo();
        var criadaEm = AGORA.minusMonths(1);
        var existente = new Assinatura(UUID.randomUUID(), DONO, "sub_1", CicloAssinatura.MENSAL,
                StatusAssinatura.ACTIVE, AGORA.plusDays(2), false, criadaEm, criadaEm);
        when(assinaturas.buscarUsuarioPorStripeCustomerId("cus_1")).thenReturn(Optional.of(DONO));
        when(assinaturas.buscarPorStripeSubscriptionId("sub_1")).thenReturn(Optional.of(existente));

        service.aplicar(EVENTO, estado(StatusAssinatura.CANCELED, null));

        var atualizada = gravada();
        assertThat(atualizada.id()).isEqualTo(existente.id());
        assertThat(atualizada.criadoEm()).isEqualTo(criadaEm);
        assertThat(atualizada.status()).isEqualTo(StatusAssinatura.CANCELED);
        assertThat(atualizada.atualizadoEm()).isEqualTo(AGORA);
    }

    @Test
    void eventoRepetidoNaoTocaNasAssinaturas() {
        when(eventos.registrarSeNovo(eq("evt_1"), eq("customer.subscription.updated"), any())).thenReturn(false);

        var resultado = service.aplicar(EVENTO, estado(StatusAssinatura.ACTIVE, null));

        assertThat(resultado).isEqualTo(ResultadoWebhook.REPETIDO);
        verifyNoInteractions(assinaturas, usuarios);
    }

    @Test
    void semClienteConhecidoAchaODonoPeloMetadata() {
        eventoNovo();
        UUID doMetadata = UUID.randomUUID();
        when(assinaturas.buscarUsuarioPorStripeCustomerId("cus_1")).thenReturn(Optional.empty());
        when(usuarios.buscarPorId(doMetadata)).thenReturn(Optional.of(
                new Usuario(doMetadata, "Ana", "ana@exemplo.com", "hash", Role.ALUNO, AGORA, AGORA)));
        when(assinaturas.buscarPorStripeSubscriptionId("sub_1")).thenReturn(Optional.empty());

        var resultado = service.aplicar(EVENTO, estado(StatusAssinatura.ACTIVE, doMetadata));

        assertThat(resultado).isEqualTo(ResultadoWebhook.PROCESSADO);
        assertThat(gravada().usuarioId()).isEqualTo(doMetadata);
    }

    @Test
    void semClienteESemMetadataNaoGrava() {
        eventoNovo();
        when(assinaturas.buscarUsuarioPorStripeCustomerId("cus_1")).thenReturn(Optional.empty());

        var resultado = service.aplicar(EVENTO, estado(StatusAssinatura.ACTIVE, null));

        assertThat(resultado).isEqualTo(ResultadoWebhook.SEM_DONO);
        verify(assinaturas, never()).salvar(any());
    }

    @Test
    void metadataDeContaQueNaoExisteNaoGrava() {
        eventoNovo();
        UUID inexistente = UUID.randomUUID();
        when(assinaturas.buscarUsuarioPorStripeCustomerId("cus_1")).thenReturn(Optional.empty());
        when(usuarios.buscarPorId(inexistente)).thenReturn(Optional.empty());

        var resultado = service.aplicar(EVENTO, estado(StatusAssinatura.ACTIVE, inexistente));

        assertThat(resultado).isEqualTo(ResultadoWebhook.SEM_DONO);
        verify(assinaturas, never()).salvar(any());
    }
}
