package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.exception.PagamentoIndisponivelException;
import eduar.studymindredacao.domain.exception.WebhookInvalidoException;
import eduar.studymindredacao.domain.model.EstadoAssinaturaStripe;
import eduar.studymindredacao.domain.model.EventoWebhook;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessarWebhookServiceTest {
    private static final String CORPO = "{\"id\":\"evt_1\"}";
    private static final String ASSINATURA = "t=1,v1=abc";
    private static final EventoWebhook DE_ASSINATURA =
            new EventoWebhook("evt_1", "customer.subscription.updated", "sub_1");
    private static final EstadoAssinaturaStripe ESTADO = new EstadoAssinaturaStripe("sub_1", "cus_1", null,
            CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE,
            OffsetDateTime.of(2026, 11, 20, 0, 0, 0, 0, ZoneOffset.UTC), false);

    @Mock
    private PagamentoGatewayPort gateway;
    @Mock
    private SincronizarAssinaturaService sincronizar;

    private ProcessarWebhookService service;

    @BeforeEach
    void criar() {
        service = new ProcessarWebhookService(gateway, sincronizar);
    }

    @Test
    void eventoQueNaoAfetaAssinaturaNaoConsultaOStripe() {
        when(gateway.validarEvento(CORPO, ASSINATURA)).thenReturn(new EventoWebhook("evt_2", "invoice.paid", null));

        assertThat(service.processar(CORPO, ASSINATURA)).isEqualTo(ResultadoWebhook.IGNORADO);

        verify(gateway, never()).buscarAssinatura(anyString());
        verifyNoInteractions(sincronizar);
    }

    @Test
    void assinaturaQueNaoExisteNoStripeEIgnorada() {
        when(gateway.validarEvento(CORPO, ASSINATURA)).thenReturn(DE_ASSINATURA);
        when(gateway.buscarAssinatura("sub_1")).thenReturn(Optional.empty());

        assertThat(service.processar(CORPO, ASSINATURA)).isEqualTo(ResultadoWebhook.IGNORADO);

        verifyNoInteractions(sincronizar);
    }

    @Test
    void casoNormalDelegaOEstadoAtualDoStripe() {
        when(gateway.validarEvento(CORPO, ASSINATURA)).thenReturn(DE_ASSINATURA);
        when(gateway.buscarAssinatura("sub_1")).thenReturn(Optional.of(ESTADO));
        when(sincronizar.aplicar(DE_ASSINATURA, ESTADO)).thenReturn(ResultadoWebhook.PROCESSADO);

        assertThat(service.processar(CORPO, ASSINATURA)).isEqualTo(ResultadoWebhook.PROCESSADO);
    }

    @Test
    void webhookInvalidoSobeSemConsultarNada() {
        when(gateway.validarEvento(CORPO, ASSINATURA))
                .thenThrow(new WebhookInvalidoException("Assinatura do webhook inválida", null));

        assertThatThrownBy(() -> service.processar(CORPO, ASSINATURA))
                .isInstanceOf(WebhookInvalidoException.class);

        verify(gateway, never()).buscarAssinatura(anyString());
        verifyNoInteractions(sincronizar);
    }

    @Test
    void stripeForaDoArSobeParaVirar503() {
        when(gateway.validarEvento(CORPO, ASSINATURA)).thenReturn(DE_ASSINATURA);
        when(gateway.buscarAssinatura("sub_1"))
                .thenThrow(new PagamentoIndisponivelException("timeout", null));

        assertThatThrownBy(() -> service.processar(CORPO, ASSINATURA))
                .isInstanceOf(PagamentoIndisponivelException.class);
    }

    @Test
    void dadoInesperadoDoStripeViraErroInternoParaOStripeRepetir() {
        // IllegalArgumentException viraria 400 no GlobalExceptionHandler, e o Stripe desistiria do evento
        when(gateway.validarEvento(CORPO, ASSINATURA)).thenReturn(DE_ASSINATURA);
        when(gateway.buscarAssinatura("sub_1"))
                .thenThrow(new IllegalArgumentException("status de assinatura desconhecido: suspended"));

        assertThatThrownBy(() -> service.processar(CORPO, ASSINATURA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("evt_1")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
