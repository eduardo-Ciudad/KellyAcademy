package eduar.studymindredacao.adapter.out.ia;

import eduar.studymindredacao.domain.julia.CompetenciaContexto;
import eduar.studymindredacao.domain.julia.ContextoJulia;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Monta a instrução de sistema da Júlia: regras do prompt + dados da correção desta redação. */
@Component
class PromptJuliaBuilder {
    static final String RECURSO = "prompts/julia-v1.txt";
    private static final String PLACEHOLDER_CONTEXTO = "{{contexto_redacao}}";

    private final String template;

    PromptJuliaBuilder() {
        this(RECURSO);
    }

    PromptJuliaBuilder(String recurso) {
        this.template = carregarTemplate(recurso);
    }

    String construir(ContextoJulia contexto) {
        Objects.requireNonNull(contexto, "contexto é obrigatório");
        return template.replace(PLACEHOLDER_CONTEXTO, formatarContexto(contexto));
    }

    static String formatarContexto(ContextoJulia contexto) {
        var texto = new StringBuilder();
        texto.append("<tema>").append(neutralizar(contexto.tema())).append("</tema>\n");
        texto.append("<nota_total>").append(contexto.notaTotal()).append("</nota_total>\n");
        if (contexto.possuiDiagnostico()) {
            texto.append("<diagnostico>").append(neutralizar(contexto.diagnostico())).append("</diagnostico>\n");
        }
        if (contexto.possuiAjusteNota()) {
            texto.append("<ajuste_de_nota>").append(neutralizar(contexto.motivoAjusteNota())).append("</ajuste_de_nota>\n");
        }
        for (CompetenciaContexto competencia : contexto.competencias()) {
            texto.append("<competencia numero=\"").append(competencia.numero())
                    .append("\" nota=\"").append(competencia.nota()).append("\">\n");
            if (!competencia.comentario().isEmpty()) {
                texto.append("Comentário da correção: ").append(neutralizar(competencia.comentario())).append('\n');
            }
            if (competencia.problemas().isEmpty()) {
                texto.append("Problemas apontados: nenhum\n");
            } else {
                texto.append("Problemas apontados:\n");
                for (String problema : competencia.problemas()) {
                    texto.append("- ").append(neutralizar(problema)).append('\n');
                }
            }
            texto.append("</competencia>\n");
        }
        texto.append("<redacao_do_aluno>\n")
                .append(neutralizar(contexto.textoRedacao()))
                .append("\n</redacao_do_aluno>");
        return texto.toString();
    }

    /** Impede que um texto do aluno feche ou abra as marcações do contexto (ex.: "</redacao_do_aluno>"). */
    static String neutralizar(String texto) {
        return texto.replace("<", "‹").replace(">", "›");
    }

    private static String carregarTemplate(String recurso) {
        try (InputStream in = new ClassPathResource(recurso).getInputStream()) {
            String conteudo = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (!conteudo.contains(PLACEHOLDER_CONTEXTO)) {
                throw new IllegalStateException(recurso + " deve conter " + PLACEHOLDER_CONTEXTO);
            }
            return conteudo;
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar " + recurso, e);
        }
    }
}
