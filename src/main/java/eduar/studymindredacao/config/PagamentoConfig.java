package eduar.studymindredacao.config;

import com.stripe.StripeClient;
import eduar.studymindredacao.adapter.out.pagamento.ConfiguracaoStripe;
import eduar.studymindredacao.adapter.out.pagamento.PagamentoDesligadoAdapter;
import eduar.studymindredacao.adapter.out.pagamento.StripePagamentoAdapter;
import eduar.studymindredacao.domain.port.PagamentoGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;


@Configuration
public class PagamentoConfig {

    @Bean
    PagamentoGatewayPort pagamentoGatewayPort(
            @Value("${app.pagamento.habilitado}") String habilitado,
            @Value("${stripe.secret-key:}") String secretKey,
            @Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${stripe.preco-pro-mensal:}") String precoMensal,
            @Value("${stripe.preco-pro-anual:}") String precoAnual,
            @Value("${app.frontend-url:}") String frontendUrl,
            @Value("${stripe.logo-file:}") String logoFile
    ) {
        var config = new ConfiguracaoStripe(secretKey, webhookSecret, precoMensal, precoAnual, frontendUrl, logoFile);
        return criar(Boolean.parseBoolean(habilitado == null ? "" : habilitado.strip()), config);
    }

    static PagamentoGatewayPort criar(boolean habilitado, ConfiguracaoStripe config) {
        if (!habilitado) {
            return new PagamentoDesligadoAdapter();
        }
        List<String> erros = config.validar();
        if (!erros.isEmpty()) {
            throw new IllegalStateException(
                    "Configuração inválida, a API não vai subir. Corrija no .env:\n- " + String.join("\n- ", erros)
            );
        }
        return new StripePagamentoAdapter(new StripeClient(config.secretKey()), config);
    }
}