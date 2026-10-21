package eduar.studymindredacao.domain.julia;

import java.util.List;

public record CompetenciaContexto(int numero, int nota, String comentario, List<String> problemas) {

    public CompetenciaContexto {
        if (numero < 1 || numero > 5) {
            throw new IllegalArgumentException("a competência deve estar entre 1 e 5");
        }
        if (nota < 0 || nota > 200) {
            throw new IllegalArgumentException("a nota da competência deve estar entre 0 e 200");
        }
        comentario = comentario == null ? "" : comentario.strip();
        problemas = problemas == null ? List.of() : List.copyOf(problemas);
    }
}
