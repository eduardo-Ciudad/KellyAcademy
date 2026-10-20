package eduar.studymindredacao.adapter.out.pagamento;

import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradutorStripeTest {

    @ParameterizedTest
    @CsvSource({
            "active,ACTIVE", "trialing,TRIALING", "past_due,PAST_DUE", "canceled,CANCELED",
            "unpaid,UNPAID", "incomplete,INCOMPLETE", "incomplete_expired,INCOMPLETE_EXPIRED", "paused,PAUSED"
    })
    void traduzTodosOsStatusDoStripe(String doStripe, StatusAssinatura esperado) {
        assertThat(TradutorStripe.status(doStripe)).isEqualTo(esperado);
    }

    @Test
    void statusDesconhecidoLancaComOValorNaMensagem() {
        assertThatThrownBy(() -> TradutorStripe.status("suspended"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suspended");
    }

    @Test
    void statusAusenteLanca() {
        assertThatThrownBy(() -> TradutorStripe.status(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mesEAnoViramOsCiclosVendidos() {
        assertThat(TradutorStripe.ciclo("month", 1L)).isEqualTo(CicloAssinatura.MENSAL);
        assertThat(TradutorStripe.ciclo("year", 1L)).isEqualTo(CicloAssinatura.ANUAL);
        assertThat(TradutorStripe.ciclo("month", null)).as("sem quantidade = 1").isEqualTo(CicloAssinatura.MENSAL);
    }

    @Test
    void cicloQueNaoVendemosELancado() {
        assertThatThrownBy(() -> TradutorStripe.ciclo("week", 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TradutorStripe.ciclo("month", 3L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void epochViraInstanteEmUtc() {
        // 1_792_454_400 = 2026-10-20T00:00:00Z (conferido em código, não de cabeça)
        assertThat(TradutorStripe.instante(1_792_454_400L))
                .isEqualTo(OffsetDateTime.of(2026, 10, 20, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void dataAusenteLanca() {
        assertThatThrownBy(() -> TradutorStripe.instante(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usuarioDoMetadata() {
        UUID id = UUID.randomUUID();
        assertThat(TradutorStripe.usuarioDoMetadata(Map.of("usuario_id", id.toString()))).isEqualTo(id);
        assertThat(TradutorStripe.usuarioDoMetadata(Map.of("usuario_id", "nao-e-uuid"))).isNull();
        assertThat(TradutorStripe.usuarioDoMetadata(Map.of())).isNull();
        assertThat(TradutorStripe.usuarioDoMetadata(null)).isNull();
    }

    @Test
    void eventoDeAssinaturaUsaOProprioId() {
        String json = "{\"id\":\"sub_123\",\"object\":\"subscription\",\"status\":\"active\"}";
        assertThat(TradutorStripe.subscriptionDoEvento("customer.subscription.updated", json)).isEqualTo("sub_123");
        assertThat(TradutorStripe.subscriptionDoEvento("customer.subscription.deleted", json)).isEqualTo("sub_123");
    }

    @Test
    void checkoutConcluidoUsaOCampoSubscription() {
        String json = "{\"id\":\"cs_test_1\",\"object\":\"checkout.session\",\"subscription\":\"sub_999\"}";
        assertThat(TradutorStripe.subscriptionDoEvento("checkout.session.completed", json)).isEqualTo("sub_999");
    }

    @Test
    void checkoutConcluidoComSubscriptionExpandida() {
        String json = "{\"id\":\"cs_test_1\",\"subscription\":{\"id\":\"sub_777\",\"object\":\"subscription\"}}";
        assertThat(TradutorStripe.subscriptionDoEvento("checkout.session.completed", json)).isEqualTo("sub_777");
    }

    @Test
    void checkoutDePagamentoAvulsoNaoTemAssinatura() {
        String json = "{\"id\":\"cs_test_1\",\"subscription\":null}";
        assertThat(TradutorStripe.subscriptionDoEvento("checkout.session.completed", json)).isNull();
    }

    @Test
    void eventosQueNaoTratamosDevolvemNulo() {
        String json = "{\"id\":\"in_1\",\"subscription\":\"sub_1\"}";
        assertThat(TradutorStripe.subscriptionDoEvento("invoice.paid", json)).isNull();
    }

    @Test
    void jsonQuebradoOuIdEstranhoDevolvemNulo() {
        assertThat(TradutorStripe.subscriptionDoEvento("customer.subscription.updated", "{nao json")).isNull();
        assertThat(TradutorStripe.subscriptionDoEvento("customer.subscription.updated", "{\"id\":\"cus_1\"}")).isNull();
        assertThat(TradutorStripe.subscriptionDoEvento("customer.subscription.updated", null)).isNull();
    }
}