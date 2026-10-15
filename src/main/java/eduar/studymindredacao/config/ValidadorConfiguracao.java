package eduar.studymindredacao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Confere a configuração durante a subida (SEC-14). Com algo faltando ou inválido, a aplicação
 * não sobe e a mensagem lista tudo o que precisa corrigir, em vez de falhar calada em produção.
 * Valores numéricos chegam como String para que um valor vazio vire mensagem clara, não erro de conversão.
 */
@Component
class ValidadorConfiguracao {
    private static final Logger log = LoggerFactory.getLogger(ValidadorConfiguracao.class);

    ValidadorConfiguracao(
            @Value("${app.limites.correcoes-diarias-free}") String correcoesDiarias,
            @Value("${app.limites.correcoes-diarias-globais}") String correcoesGlobais,
            @Value("${app.limites.transcricoes-diarias-free}") String transcricoesDiarias,
            @Value("${app.limites.transcricoes-diarias-globais}") String transcricoesGlobais,
            @Value("${gemini.modelo}") String modelo,
            @Value("${gemini.transcricao.modelo}") String modeloTranscricao,
            @Value("${app.email.habilitado}") String emailHabilitado,
            @Value("${spring.mail.host}") String mailHost,
            @Value("${spring.mail.port}") String mailPorta,
            @Value("${spring.mail.username}") String mailUsuario,
            @Value("${spring.mail.password}") String mailSenha,
            @Value("${app.frontend-url}") String frontendUrl
    ) {
        var config = new ConfiguracaoInicial(
                inteiro(correcoesDiarias),
                inteiro(correcoesGlobais),
                inteiro(transcricoesDiarias),
                inteiro(transcricoesGlobais),
                modelo,
                modeloTranscricao,
                Boolean.parseBoolean(emailHabilitado == null ? "" : emailHabilitado.strip()),
                mailHost,
                mailPorta,
                mailUsuario,
                mailSenha,
                frontendUrl
        );
        List<String> erros = config.validar();
        if (!erros.isEmpty()) {
            throw new IllegalStateException(
                    "Configuração inválida, a API não vai subir. Corrija no .env:\n- " + String.join("\n- ", erros)
            );
        }
        log.info("Configuração em vigor: {}", config.resumoSeguro());
    }

    /** Valor vazio ou não numérico vira 0, que a validação recusa com o nome da variável. */
    private static int inteiro(String valor) {
        try {
            return Integer.parseInt(valor == null ? "" : valor.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
