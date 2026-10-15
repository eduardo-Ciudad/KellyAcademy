package eduar.studymindredacao.domain.model;

import java.util.List;

/**
 * Medidas exatas do texto do aluno, calculadas pelo sistema antes da correção e enviadas à IA
 * como dado: a IA julga se cada repetição é inadequada, mas não precisa encontrá-las sozinha.
 *
 * @param paragrafos  quantidade de parágrafos
 * @param palavras    quantidade de palavras
 * @param aberturas   as primeiras palavras de cada parágrafo (ajudam a achar os operadores entre parágrafos)
 * @param repeticoes  repetições encontradas, das mais frequentes para as menos
 */
public record AnaliseTexto(int paragrafos, int palavras, List<String> aberturas, List<Repeticao> repeticoes) {

    public static final AnaliseTexto VAZIA = new AnaliseTexto(0, 0, List.of(), List.of());

    public enum TipoRepeticao {
        /** Mesmo radical várias vezes no texto. */
        PALAVRA,
        /** Mesmo par de palavras em parágrafos diferentes. */
        EXPRESSAO,
        /** Palavra longa repetida dentro de um mesmo parágrafo. */
        MESMO_PARAGRAFO
    }

    /**
     * @param formas      como a palavra ou expressão aparece no texto (minúsculas, só letras)
     * @param ocorrencias quantas vezes aparece
     * @param paragrafos  em quais parágrafos aparece (a partir de 1)
     */
    public record Repeticao(List<String> formas, int ocorrencias, List<Integer> paragrafos, TipoRepeticao tipo) {
        public Repeticao {
            formas = List.copyOf(formas);
            paragrafos = List.copyOf(paragrafos);
            if (formas.isEmpty() || ocorrencias < 2 || tipo == null) {
                throw new IllegalArgumentException("repetição precisa de forma, tipo e ao menos 2 ocorrências");
            }
        }
    }

    public AnaliseTexto {
        if (paragrafos < 0 || palavras < 0) {
            throw new IllegalArgumentException("contagens não podem ser negativas");
        }
        aberturas = aberturas == null ? List.of() : List.copyOf(aberturas);
        repeticoes = repeticoes == null ? List.of() : List.copyOf(repeticoes);
    }
}
