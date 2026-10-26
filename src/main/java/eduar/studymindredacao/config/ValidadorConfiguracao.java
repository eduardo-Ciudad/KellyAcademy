package eduar.studymindredacao.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Confere a configuração durante a subida (SEC-14). Com algo faltando ou inválido, a aplicação
 * não sobe e a mensagem lista tudo o que precisa corrigir, em vez de falhar calada em produção.
 * Valores numéricos chegam como String para que um valor vazio vire mensagem clara, não erro de conversão.
 * As chaves do Gemini são conferidas à parte ({@link ChavesGemini}) e nunca vão para o log.
 */
@Component
class ValidadorConfiguracao {
    private static final Logger log = LoggerFactory.getLogger(ValidadorConfiguracao.class);

    ValidadorConfiguracao(
            @Value("${app.limites.correcoes-diarias-globais}") String correcoesGlobais,
            @Value("${app.limites.transcricoes-diarias-globais}") String transcricoesGlobais,
            @Value("${gemini.modelo}") String modelo,
            @Value("${gemini.transcricao.modelo}") String modeloTranscricao,
            @Value("${app.email.habilitado}") String emailHabilitado,
            @Value("${spring.mail.host}") String mailHost,
            @Value("${spring.mail.port}") String mailPorta,
            @Value("${spring.mail.username}") String mailUsuario,
            @Value("${spring.mail.password}") String mailSenha,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${gemini.api-key}") String chaveCorrecao,
            @Value("${julia.gemini.api-key}") String chaveJulia,
            @Value("${transcricao.gemini.api-key}") String chaveTranscricao
    ) {
        var config = new ConfiguracaoInicial(
                inteiro(correcoesGlobais),
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
        var chaves = new ChavesGemini(chaveCorrecao, chaveJulia, chaveTranscricao);

        List<String> erros = new ArrayList<>(config.validar());
        erros.addAll(chaves.validar());
        if (!erros.isEmpty()) {
            throw new IllegalStateException(
                    "Configuração inválida, a API não vai subir. Corrija no .env:\n- " + String.join("\n- ", erros)
            );
        }
        log.info("Configuração em vigor: {}, {}", config.resumoSeguro(), chaves);
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
