package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.model.SolicitacaoAvaliacao;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptAvaliacaoBuilder {

    private static final String RECURSO = "prompts/avaliacao-v3.txt";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(tema|texto)}}");
    private static final Pattern TAG_REDACAO = Pattern.compile("(?i)</?redacao>");

    private final String template;

    public PromptAvaliacaoBuilder() {
        this.template = carregarTemplate();
    }

    public String construir(SolicitacaoAvaliacao solicitacao) {
        Map<String, String> valores = Map.of(
                "tema", neutralizar(solicitacao.tema()),
                "texto", neutralizar(solicitacao.textoRedacao()));

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder prompt = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(prompt, Matcher.quoteReplacement(valores.get(matcher.group(1))));
        }
        matcher.appendTail(prompt);
        return prompt.toString();
    }

    private static String neutralizar(String valor) {
        return TAG_REDACAO.matcher(valor).replaceAll("");
    }

    private static String carregarTemplate() {
        try (InputStream in = new ClassPathResource(RECURSO).getInputStream()) {
            String conteudo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!conteudo.contains("{{tema}}") || !conteudo.contains("{{texto}}")) {
                throw new IllegalStateException(RECURSO + " deve conter {{tema}} e {{texto}}");
            }
            return conteudo;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar " + RECURSO, e);
        }
    }
}