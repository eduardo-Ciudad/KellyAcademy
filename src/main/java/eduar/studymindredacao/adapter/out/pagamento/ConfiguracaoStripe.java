package eduar.studymindredacao.adapter.out.pagamento;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuração do Stripe lida do application.properties. {@code validar} lista tudo o que falta
 * com o nome da variável de ambiente, no mesmo estilo do ValidadorConfiguracao.
 */
public record ConfiguracaoStripe(
        String secretKey,
        String webhookSecret,
        String lookupKeyMensal,
        String lookupKeyAnual,
        String frontendUrl,
        String logoFileId
) {
    public ConfiguracaoStripe {
        secretKey = limpar(secretKey);
        webhookSecret = limpar(webhookSecret);
        lookupKeyMensal = limpar(lookupKeyMensal);
        lookupKeyAnual = limpar(lookupKeyAnual);
        frontendUrl = limpar(frontendUrl);
        if (frontendUrl != null && frontendUrl.endsWith("/")) {
            frontendUrl = frontendUrl.substring(0, frontendUrl.length() - 1);
        }
        logoFileId = limpar(logoFileId);
    }

    public List<String> validar() {
        var erros = new ArrayList<String>();
        // sk_ = chave secreta completa; rk_ = chave restrita (recomendada: só as permissões que o backend usa)
        if (secretKey == null || !(secretKey.startsWith("sk_") || secretKey.startsWith("rk_"))) {
            erros.add("STRIPE_SECRET_KEY ausente ou inválida (esperado sk_... ou rk_...)");
        }
        if (webhookSecret == null || !webhookSecret.startsWith("whsec_")) {
            erros.add("STRIPE_WEBHOOK_SECRET ausente ou inválido (esperado whsec_...)");
        }
        if (lookupKeyMensal == null) {
            erros.add("STRIPE_PRECO_PRO_MENSAL vazio");
        }
        if (lookupKeyAnual == null) {
            erros.add("STRIPE_PRECO_PRO_ANUAL vazio");
        }
        if (frontendUrl == null) {
            erros.add("APP_FRONTEND_URL vazio (usado nas URLs de retorno do checkout)");
        }
        if (logoFileId != null && !logoFileId.startsWith("file_")) {
            erros.add("STRIPE_LOGO_FILE inválido (esperado file_... ou vazio)");
        }
        return erros;
    }

    /** Nunca loga as chaves. */
    @Override
    public String toString() {
        return "ConfiguracaoStripe[secretKey=***, webhookSecret=***, lookupKeyMensal=" + lookupKeyMensal
                + ", lookupKeyAnual=" + lookupKeyAnual + ", frontendUrl=" + frontendUrl
                + ", logoFileId=" + logoFileId + "]";
    }

    private static String limpar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.strip();
    }
}
