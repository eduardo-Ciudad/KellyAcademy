package eduar.studymindredacao.adapter.out.pagamento;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.param.checkout.SessionCreateParams;
import eduar.studymindredacao.domain.exception.WebhookInvalidoException;
import eduar.studymindredacao.domain.model.SolicitacaoCheckout;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sem rede: testa a montagem dos parâmetros e a verificação de assinatura do webhook (HMAC real). */
class StripePagamentoAdapterTest {
    private static final String SEGREDO = "whsec_teste_segredo";
    private static final ConfiguracaoStripe CONFIG = new ConfiguracaoStripe("sk_test_falsa", SEGREDO,
            "kelly_pro_mensal", "kelly_pro_anual", "https://kellyacademy.com.br/", "file_logo123");
    private static final UUID USUARIO = UUID.randomUUID();

    @Nested
    class ParametrosDaSessao {
        private final SessionCreateParams params = StripePagamentoAdapter.montarParametrosSessao(
                new SolicitacaoCheckout(USUARIO, "cus_abc", CicloAssinatura.MENSAL), "price_mensal", CONFIG);

        @Test
        void assinaturaParaOClienteDoAlunoComOPrecoDoCiclo() {
            assertThat(params.getMode()).isEqualTo(SessionCreateParams.Mode.SUBSCRIPTION);
            assertThat(params.getCustomer()).isEqualTo("cus_abc");
            assertThat(params.getLineItems()).singleElement().satisfies(item -> {
                assertThat(item.getPrice()).isEqualTo("price_mensal");
                assertThat(item.getQuantity()).isEqualTo(1L);
            });
        }

        @Test
        void identificaOAlunoNaSessaoENaAssinatura() {
            assertThat(params.getClientReferenceId()).isEqualTo(USUARIO.toString());
            assertThat(params.getSubscriptionData().getMetadata()).containsEntry("usuario_id", USUARIO.toString());
        }

        @Test
        void urlsDeRetornoSemBarraDupla() {
            assertThat(params.getSuccessUrl())
                    .isEqualTo("https://kellyacademy.com.br/assinatura/sucesso?session_id={CHECKOUT_SESSION_ID}");
            assertThat(params.getCancelUrl()).isEqualTo("https://kellyacademy.com.br/planos");
        }

        @Test
        void marcaDaKellyAcademy() {
            var marca = params.getBrandingSettings();
            assertThat(marca.getDisplayName()).isEqualTo("Kelly Academy");
            assertThat(marca.getBackgroundColor()).isEqualTo("#0b0c12");
            assertThat(marca.getButtonColor()).isEqualTo("#2f5bff");
            assertThat(marca.getFontFamily()).isEqualTo(SessionCreateParams.BrandingSettings.FontFamily.INTER);
            assertThat(marca.getLogo().getFile()).isEqualTo("file_logo123");
        }

        @Test
        void semLogoConfiguradoNaoMandaLogo() {
            var semLogo = new ConfiguracaoStripe("sk_test_falsa", SEGREDO, "m", "a", "https://x.com", null);
            var p = StripePagamentoAdapter.montarParametrosSessao(
                    new SolicitacaoCheckout(USUARIO, "cus_abc", CicloAssinatura.ANUAL), "price_anual", semLogo);

            assertThat(p.getBrandingSettings().getLogo()).isNull();
        }

        @Test
        void identificadoresDoCheckoutStudio() {
            assertThat(params.getExtraParams())
                    .containsEntry("integration_identifier", "hosted_web_0001")
                    .containsEntry("origin_context", "web");
        }
    }

    @Nested
    class ParametrosDoPortal {
        private final com.stripe.param.billingportal.SessionCreateParams params =
                StripePagamentoAdapter.montarParametrosPortal("cus_abc", CONFIG);

        @Test
        void abreOPortalDoClienteDoAluno() {
            assertThat(params.getCustomer()).isEqualTo("cus_abc");
        }

        @Test
        void voltaParaPlanosSemBarraDuplaEComMarcaDeRetorno() {
            // CONFIG tem frontendUrl com barra no fim: o ConfiguracaoStripe tira
            assertThat(params.getReturnUrl()).isEqualTo("https://kellyacademy.com.br/planos?portal=retorno");
        }

        @Test
        void portalEmPortugues() {
            assertThat(params.getLocale())
                    .isEqualTo(com.stripe.param.billingportal.SessionCreateParams.Locale.PT_BR);
        }

        @Test
        void naoFixaConfiguracaoDoPortal() {
            // Usa a configuração padrão do Dashboard (sem troca de plano, cancela no fim do período)
            assertThat(params.getConfiguration()).isNull();
        }
    }

    @Nested
    class Webhook {
        private final StripePagamentoAdapter adapter =
                new StripePagamentoAdapter(new StripeClient("sk_test_falsa"), CONFIG);

        private static String evento(String id, String tipo, String objetoJson) {
            return "{\"id\":\"" + id + "\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION
                    + "\",\"type\":\"" + tipo + "\",\"data\":{\"object\":" + objetoJson + "}}";
        }

        /** Mesmo formato do header Stripe-Signature: t=<timestamp>,v1=<HMAC-SHA256 de "t.corpo">. */
        private static String assinar(String corpo, String segredo) throws Exception {
            long t = System.currentTimeMillis() / 1000;
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String hmac = HexFormat.of().formatHex(mac.doFinal((t + "." + corpo).getBytes(StandardCharsets.UTF_8)));
            return "t=" + t + ",v1=" + hmac;
        }

        @Test
        void eventoAssinadoCorretamenteDevolveIdTipoEAssinatura() throws Exception {
            String corpo = evento("evt_1", "customer.subscription.updated",
                    "{\"id\":\"sub_123\",\"object\":\"subscription\"}");

            var evento = adapter.validarEvento(corpo, assinar(corpo, SEGREDO));

            assertThat(evento.id()).isEqualTo("evt_1");
            assertThat(evento.tipo()).isEqualTo("customer.subscription.updated");
            assertThat(evento.stripeSubscriptionId()).isEqualTo("sub_123");
        }

        @Test
        void eventoQueNaoMexeEmAssinaturaVemSemSubscription() throws Exception {
            String corpo = evento("evt_2", "invoice.paid", "{\"id\":\"in_1\",\"object\":\"invoice\"}");

            var evento = adapter.validarEvento(corpo, assinar(corpo, SEGREDO));

            assertThat(evento.afetaAssinatura()).isFalse();
        }

        @Test
        void corpoAlteradoDepoisDeAssinadoERecusado() throws Exception {
            String corpo = evento("evt_3", "customer.subscription.updated", "{\"id\":\"sub_123\"}");
            String assinatura = assinar(corpo, SEGREDO);
            String adulterado = corpo.replace("sub_123", "sub_hacker");

            assertThatThrownBy(() -> adapter.validarEvento(adulterado, assinatura))
                    .isInstanceOf(WebhookInvalidoException.class);
        }

        @Test
        void assinadoComOutroSegredoERecusado() throws Exception {
            String corpo = evento("evt_4", "customer.subscription.updated", "{\"id\":\"sub_123\"}");

            assertThatThrownBy(() -> adapter.validarEvento(corpo, assinar(corpo, "whsec_outro")))
                    .isInstanceOf(WebhookInvalidoException.class);
        }

        @Test
        void semAssinaturaOuSemCorpoERecusado() {
            assertThatThrownBy(() -> adapter.validarEvento("{}", null)).isInstanceOf(WebhookInvalidoException.class);
            assertThatThrownBy(() -> adapter.validarEvento(" ", "t=1,v1=abc"))
                    .isInstanceOf(WebhookInvalidoException.class);
        }
    }
}