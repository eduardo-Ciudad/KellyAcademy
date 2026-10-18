package eduar.studymindredacao.application.usecase;

import java.time.Duration;
import java.util.Objects;

/**
 * Regras do "Esqueci minha senha" vindas da configuração. Com {@code habilitada=false} (EMAIL_HABILITADO=false)
 * não sai link nenhum e a troca com a conta logada não manda o aviso.
 */
public record PoliticaRedefinicaoSenha(boolean habilitada, Duration validade, String urlBaseFrontend) {
    static final String CAMINHO_REDEFINICAO = "/redefinir-senha#token=";
    static final String CAMINHO_ESQUECI_SENHA = "/esqueci-senha";

    public PoliticaRedefinicaoSenha {
        Objects.requireNonNull(validade, "validade é obrigatória");
        if (validade.isZero() || validade.isNegative()) {
            throw new IllegalArgumentException("validade deve ser positiva");
        }
        urlBaseFrontend = urlBaseFrontend == null ? "" : urlBaseFrontend.strip();
        if (habilitada && urlBaseFrontend.isEmpty()) {
            throw new IllegalArgumentException("APP_FRONTEND_URL é obrigatória com o envio de e-mail ligado");
        }
        if (urlBaseFrontend.endsWith("/")) {
            throw new IllegalArgumentException("APP_FRONTEND_URL não deve terminar com barra");
        }
    }

    public static PoliticaRedefinicaoSenha desligada() {
        return new PoliticaRedefinicaoSenha(false, Duration.ofMinutes(30), "");
    }

    /** O token vai depois do "#": não chega ao servidor do front nem ao Referer; a página faz o POST. */
    String linkPara(String token) {
        return urlBaseFrontend + CAMINHO_REDEFINICAO + token;
    }

    /** Página onde o aluno pede um link novo (vai no aviso de senha alterada, sem token). */
    String linkEsqueciSenha() {
        return urlBaseFrontend + CAMINHO_ESQUECI_SENHA;
    }
}
