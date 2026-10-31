package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.PagamentoIndisponivelException;
import eduar.studymindredacao.domain.exception.SemClientePagamentoException;
import eduar.studymindredacao.domain.model.SessaoPortal;
import eduar.studymindredacao.domain.port.AssinaturaRepositoryPort;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbrirPortalAssinaturaServiceTest {
    private static final UUID ID = UUID.randomUUID();
    private static final SessaoPortal SESSAO =
            new SessaoPortal("bps_test_1", "https://billing.stripe.com/p/session/test_1");

    @Mock
    private AssinaturaRepositoryPort assinaturas;
    @Mock
    private PagamentoGatewayPort gateway;

    private AbrirPortalAssinaturaService service;

    @BeforeEach
    void criar() {
        service = new AbrirPortalAssinaturaService(assinaturas, gateway);
    }

    @Test
    void comClienteNoStripeAbreOPortalDesseCliente() {
        when(assinaturas.buscarStripeCustomerId(ID)).thenReturn(Optional.of("cus_abc"));
        when(gateway.criarSessaoPortal("cus_abc")).thenReturn(SESSAO);

        assertThat(service.abrir(ID)).isEqualTo(SESSAO);
    }

    @Test
    void naoOlhaAAssinaturaExAssinanteTambemAbre() {
        // Decisão: basta ter cliente no Stripe, para o ex-assinante ver as faturas
        when(assinaturas.buscarStripeCustomerId(ID)).thenReturn(Optional.of("cus_abc"));
        when(gateway.criarSessaoPortal("cus_abc")).thenReturn(SESSAO);

        service.abrir(ID);

        verify(assinaturas, never()).listarPorUsuario(ID);
    }

    @Test
    void semClienteNoStripeRecusaSemChamarOStripe() {
        when(assinaturas.buscarStripeCustomerId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abrir(ID)).isInstanceOf(SemClientePagamentoException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void naoCriaClienteNoStripe() {
        when(assinaturas.buscarStripeCustomerId(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.abrir(ID)).isInstanceOf(SemClientePagamentoException.class);
        verify(gateway, never()).criarCliente(any(), anyString(), anyString());
        verify(assinaturas, never()).vincularStripeCustomerId(any(), anyString());
    }

    @Test
    void stripeForaDoArPropagaIndisponivel() {
        when(assinaturas.buscarStripeCustomerId(ID)).thenReturn(Optional.of("cus_abc"));
        when(gateway.criarSessaoPortal("cus_abc"))
                .thenThrow(new PagamentoIndisponivelException("fora do ar", null));

        assertThatThrownBy(() -> service.abrir(ID)).isInstanceOf(PagamentoIndisponivelException.class);
    }

    @Test
    void exigeUsuario() {
        assertThatThrownBy(() -> service.abrir(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("usuarioId");
        verifyNoInteractions(assinaturas, gateway);
    }
}
