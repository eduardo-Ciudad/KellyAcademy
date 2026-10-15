package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.model.AnaliseTexto;
import eduar.studymindredacao.domain.model.AnaliseTexto.Repeticao;
import eduar.studymindredacao.domain.model.SolicitacaoAvaliacao;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptAvaliacaoBuilder {

    private static final String RECURSO = "prompts/avaliacao-v3.txt";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(tema|texto|dados_texto)}}");
    private static final Pattern TAG_REDACAO = Pattern.compile("(?i)</?redacao>");

    private final String template;

    public PromptAvaliacaoBuilder() {
        this.template = carregarTemplate();
    }

    public String construir(SolicitacaoAvaliacao solicitacao) {
        Map<String, String> valores = Map.of(
                "tema", neutralizar(solicitacao.tema()),
                "texto", neutralizar(solicitacao.textoRedacao()),
                "dados_texto", neutralizar(dadosDoTexto(solicitacao.analise())));

        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder prompt = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(prompt, Matcher.quoteReplacement(valores.get(matcher.group(1))));
        }
        matcher.appendTail(prompt);
        return prompt.toString();
    }

    /** Bloco "DADOS AUTOMÁTICOS DO TEXTO" do prompt: contagens exatas da pré-análise (#27). */
    static String dadosDoTexto(AnaliseTexto analise) {
        if (analise == null || analise.paragrafos() == 0) {
            return "Não disponível.";
        }
        var dados = new StringBuilder();
        dados.append("- Parágrafos: ").append(analise.paragrafos())
                .append(" · Palavras: ").append(analise.palavras()).append('\n');
        List<String> aberturas = analise.aberturas();
        dados.append("- Abertura dos parágrafos:");
        for (int i = 0; i < aberturas.size(); i++) {
            dados.append(i == 0 ? " " : " · ").append('§').append(i + 1).append(" \"").append(aberturas.get(i)).append('"');
        }
        dados.append('\n');
        if (analise.repeticoes().isEmpty()) {
            dados.append("- Repetições encontradas: nenhuma.");
            return dados.toString();
        }
        dados.append("- Repetições encontradas:");
        for (Repeticao repeticao : analise.repeticoes()) {
            dados.append("\n  - ").append(descrever(repeticao));
        }
        return dados.toString();
    }

    private static String descrever(Repeticao repeticao) {
        String formas = "\"" + String.join("/", repeticao.formas()) + "\"";
        String paragrafos = "§" + String.join(", §", repeticao.paragrafos().stream().map(String::valueOf).toList());
        return switch (repeticao.tipo()) {
            case PALAVRA -> formas + " " + repeticao.ocorrencias() + "x no texto (" + paragrafos + ")";
            case EXPRESSAO -> "expressão " + formas + " em parágrafos diferentes (" + paragrafos + ")";
            case MESMO_PARAGRAFO -> formas + " " + repeticao.ocorrencias() + "x no mesmo parágrafo (" + paragrafos + ")";
        };
    }

    private static String neutralizar(String valor) {
        return TAG_REDACAO.matcher(valor).replaceAll("");
    }

    private static String carregarTemplate() {
        try (InputStream in = new ClassPathResource(RECURSO).getInputStream()) {
            String conteudo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!conteudo.contains("{{tema}}") || !conteudo.contains("{{texto}}") || !conteudo.contains("{{dados_texto}}")) {
                throw new IllegalStateException(RECURSO + " deve conter {{tema}}, {{texto}} e {{dados_texto}}");
            }
            return conteudo;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar " + RECURSO, e);
        }
    }
}