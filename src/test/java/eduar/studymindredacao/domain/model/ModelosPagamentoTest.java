package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelosPagamentoTest {
    private static final UUID USUARIO = UUID.randomUUID();
    private static final OffsetDateTime FIM = OffsetDateTime.of(2026, 11, 20, 17, 0, 0, 0, ZoneOffset.ofHours(-3));

    @Nested
    class Solicitacao {
        @Test
        void aceitaClienteDoStripe() {
            var s = new SolicitacaoCheckout(USUARIO, "cus_abc", CicloAssinatura.ANUAL);
            assertThat(s.ciclo()).isEqualTo(CicloAssinatura.ANUAL);
        }

        @Test
        void rejeitaIdQueNaoEDeCliente() {
            assertThatThrownBy(() -> new SolicitacaoCheckout(USUARIO, "sub_abc", CicloAssinatura.MENSAL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cus_");
        }

        @Test
        void exigeCiclo() {
            assertThatThrownBy(() -> new SolicitacaoCheckout(USUARIO, "cus_abc", null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("ciclo");
        }
    }

    @Nested
    class Sessao {
        @Test
        void aceitaSessaoHttps() {
            var s = new SessaoCheckout("cs_test_abc", "https://checkout.stripe.com/c/pay/cs_test_abc");
            assertThat(s.url()).startsWith("https://checkout.stripe.com");
        }

        @Test
        void rejeitaUrlSemHttps() {
            assertThatThrownBy(() -> new SessaoCheckout("cs_test_abc", "http://checkout.stripe.com/x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("https");
        }

        @Test
        void rejeitaIdQueNaoEDeSessao() {
            assertThatThrownBy(() -> new SessaoCheckout("pi_abc", "https://checkout.stripe.com/x"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cs_");
        }
    }

    @Nested
    class Evento {
        @Test
        void eventoDeAssinaturaAfetaAssinatura() {
            var e = new EventoWebhook("evt_1", "customer.subscription.updated", "sub_1");
            assertThat(e.afetaAssinatura()).isTrue();
        }

        @Test
        void eventoSemAssinaturaNaoAfeta() {
            var e = new EventoWebhook("evt_1", "invoice.paid", null);
            assertThat(e.afetaAssinatura()).isFalse();
        }

        @Test
        void rejeitaIdQueNaoEDeEvento() {
            assertThatThrownBy(() -> new EventoWebhook("abc", "invoice.paid", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("evt_");
        }

        @Test
        void rejeitaSubscriptionComPrefixoErrado() {
            assertThatThrownBy(() -> new EventoWebhook("evt_1", "customer.subscription.updated", "cus_1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sub_");
        }
    }

    @Nested
    class Estado {
        @Test
        void aceitaSemUsuarioNoMetadata() {
            var e = new EstadoAssinaturaStripe("sub_1", "cus_1", null, CicloAssinatura.MENSAL,
                    StatusAssinatura.ACTIVE, FIM, false);
            assertThat(e.usuarioIdMetadata()).isNull();
        }

        @Test
        void rejeitaClienteComPrefixoErrado() {
            assertThatThrownBy(() -> new EstadoAssinaturaStripe("sub_1", "acct_1", USUARIO,
                    CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE, FIM, false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cus_");
        }

        @Test
        void exigePeriodo() {
            assertThatThrownBy(() -> new EstadoAssinaturaStripe("sub_1", "cus_1", USUARIO,
                    CicloAssinatura.MENSAL, StatusAssinatura.ACTIVE, null, false))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("periodoAtualFim");
        }
    }
}