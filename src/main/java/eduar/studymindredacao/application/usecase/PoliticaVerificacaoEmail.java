package eduar.studymindredacao.application.usecase;

import java.time.Duration;
import java.util.Objects;

/**
 * Regras da verificação de e-mail vindas da configuração. Com {@code obrigatoria=false} (EMAIL_HABILITADO=false)
 * as contas nascem verificadas e o login não confere nada: é o comportamento de antes da feature.
 */
public record PoliticaVerificacaoEmail(boolean obrigatoria, Duration validade, String urlBaseFrontend) {
    static final String CAMINHO_VERIFICACAO = "/verificar-email#token=";

    public PoliticaVerificacaoEmail {
        Objects.requireNonNull(validade, "validade é obrigatória");
        if (validade.isZero() || validade.isNegative()) {
            throw new IllegalArgumentException("validade deve ser positiva");
        }
        urlBaseFrontend = urlBaseFrontend == null ? "" : urlBaseFrontend.strip();
        if (obrigatoria && urlBaseFrontend.isEmpty()) {
            throw new IllegalArgumentException("APP_FRONTEND_URL é obrigatória com a verificação de e-mail ligada");
        }
        if (urlBaseFrontend.endsWith("/")) {
            throw new IllegalArgumentException("APP_FRONTEND_URL não deve terminar com barra");
        }
    }

    public static PoliticaVerificacaoEmail desligada() {
        return new PoliticaVerificacaoEmail(false, Duration.ofHours(24), "");
    }

    /**
     * Link do e-mail. O token vai depois do "#": o navegador não envia o fragmento ao servidor do front
     * (nem em log de acesso nem no Referer); a página lê e faz o POST para a API.
     */
    String linkPara(String token) {
        return urlBaseFrontend + CAMINHO_VERIFICACAO + token;
    }
}
