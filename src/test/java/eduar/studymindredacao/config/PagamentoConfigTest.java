package eduar.studymindredacao.config;

import eduar.studymindredacao.adapter.out.pagamento.ConfiguracaoStripe;
import eduar.studymindredacao.adapter.out.pagamento.PagamentoDesligadoAdapter;
import eduar.studymindredacao.adapter.out.pagamento.StripePagamentoAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoConfigTest {
    private static final ConfiguracaoStripe COMPLETA = new ConfiguracaoStripe("sk_test_x", "whsec_x",
            "kelly_pro_mensal", "kelly_pro_anual", "https://kellyacademy.com.br", "");

    @Test
    void desligadoNaoExigeChaves() {
        var vazia = new ConfiguracaoStripe("", "", "", "", "", "");

        assertThat(PagamentoConfig.criar(false, vazia)).isInstanceOf(PagamentoDesligadoAdapter.class);
    }

    @Test
    void ligadoECompletoUsaOStripe() {
        assertThat(PagamentoConfig.criar(true, COMPLETA)).isInstanceOf(StripePagamentoAdapter.class);
    }

    @Test
    void ligadoSemChavesNaoSobeEListaTudoQueFalta() {
        var vazia = new ConfiguracaoStripe("", "", "", "", "", "");

        assertThatThrownBy(() -> PagamentoConfig.criar(true, vazia))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRIPE_SECRET_KEY")
                .hasMessageContaining("STRIPE_WEBHOOK_SECRET")
                .hasMessageContaining("APP_FRONTEND_URL");
    }

    @Test
    void chavePublicavelNoLugarDaSecretaERecusada() {
        var errada = new ConfiguracaoStripe("pk_test_x", "whsec_x", "m", "a", "https://x.com", null);

        assertThatThrownBy(() -> PagamentoConfig.criar(true, errada))
                .hasMessageContaining("STRIPE_SECRET_KEY");
    }

    @Test
    void toStringNaoExpoeAsChaves() {
        assertThat(COMPLETA.toString()).doesNotContain("sk_test_x").doesNotContain("whsec_x");
    }
}