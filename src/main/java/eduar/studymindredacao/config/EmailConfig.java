package eduar.studymindredacao.config;

import eduar.studymindredacao.adapter.out.email.EnvioEmailDesligadoAdapter;
import eduar.studymindredacao.adapter.out.email.ModeloEmailRedefinicaoSenha;
import eduar.studymindredacao.adapter.out.email.ModeloEmailSenhaAlterada;
import eduar.studymindredacao.adapter.out.email.ModeloEmailVerificacao;
import eduar.studymindredacao.adapter.out.email.ModelosEmail;
import eduar.studymindredacao.adapter.out.email.SmtpEnvioEmailAdapter;
import eduar.studymindredacao.application.usecase.PoliticaRedefinicaoSenha;
import eduar.studymindredacao.application.usecase.PoliticaVerificacaoEmail;
import eduar.studymindredacao.domain.port.EnvioEmailPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

/**
 * Escolhe o envio de e-mail pelo EMAIL_HABILITADO. O executor do SMTP não é um bean de propósito:
 * um bean Executor desligaria o executor padrão do Spring Boot. Ele vive dentro do adapter, que o
 * encerra no close() quando a aplicação desliga.
 */
@Configuration
public class EmailConfig {

    @Bean
    EnvioEmailPort envioEmailPort(
            @Value("${app.email.habilitado}") String habilitado,
            @Value("${app.email.verificacao-validade}") String validade,
            @Value("${app.email.redefinicao-validade}") String validadeRedefinicao,
            @Value("${app.email.fila-maxima}") int filaMaxima,
            @Value("${spring.mail.username:}") String remetente,
            @Value("${app.email.remetente-nome:Kelly Academy}") String nomeRemetente,
            ObjectProvider<JavaMailSender> mailSender
    ) {
        return criar(Boolean.parseBoolean(habilitado == null ? "" : habilitado.strip()),
                validadeVerificacao(validade), validadeRedefinicao(validadeRedefinicao), filaMaxima, remetente,
                nomeRemetente, mailSender::getObject);
    }

    @Bean
    PoliticaVerificacaoEmail politicaVerificacaoEmail(
            @Value("${app.email.habilitado}") String habilitado,
            @Value("${app.email.verificacao-validade}") String validade,
            @Value("${app.frontend-url:}") String frontendUrl
    ) {
        return new PoliticaVerificacaoEmail(Boolean.parseBoolean(habilitado == null ? "" : habilitado.strip()),
                validadeVerificacao(validade), frontendUrl);
    }

    @Bean
    PoliticaRedefinicaoSenha politicaRedefinicaoSenha(
            @Value("${app.email.habilitado}") String habilitado,
            @Value("${app.email.redefinicao-validade}") String validade,
            @Value("${app.frontend-url:}") String frontendUrl
    ) {
        return new PoliticaRedefinicaoSenha(Boolean.parseBoolean(habilitado == null ? "" : habilitado.strip()),
                validadeRedefinicao(validade), frontendUrl);
    }

    static EnvioEmailPort criar(
            boolean habilitado,
            Duration validade,
            Duration validadeRedefinicao,
            int filaMaxima,
            String remetente,
            String nomeRemetente,
            Supplier<JavaMailSender> mailSender
    ) {
        // Desligado: o JavaMailSender nem é pedido
        if (!habilitado) {
            return new EnvioEmailDesligadoAdapter();
        }
        return new SmtpEnvioEmailAdapter(
                mailSender.get(),
                SmtpEnvioEmailAdapter.criarExecutor(filaMaxima),
                remetente,
                nomeRemetente,
                new ModelosEmail(
                        new ModeloEmailVerificacao(validade),
                        new ModeloEmailRedefinicaoSenha(validadeRedefinicao),
                        new ModeloEmailSenhaAlterada()
                )
        );
    }

    static Duration validadeVerificacao(String valor) {
        return lerValidade(valor, "app.email.verificacao-validade", "PT24H");
    }

    /** Validade do link de "Esqueci minha senha". Também usada pelo caso de uso (bloco 5) para o token. */
    public static Duration validadeRedefinicao(String valor) {
        return lerValidade(valor, "app.email.redefinicao-validade", "PT30M");
    }

    private static Duration lerValidade(String valor, String propriedade, String exemplo) {
        try {
            Duration validade = Duration.parse(valor == null ? "" : valor.strip());
            if (validade.isZero() || validade.isNegative()) {
                throw new IllegalStateException(propriedade + " deve ser positiva");
            }
            return validade;
        } catch (DateTimeParseException e) {
            throw new IllegalStateException(propriedade + " inválida (use ISO-8601, ex.: " + exemplo + ")", e);
        }
    }
}
