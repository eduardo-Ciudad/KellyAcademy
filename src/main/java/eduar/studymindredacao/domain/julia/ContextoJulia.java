package eduar.studymindredacao.domain.julia;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ContextoJulia(
        String tema,
        String textoRedacao,
        int notaTotal,
        List<CompetenciaContexto> competencias,
        String diagnostico,
        String motivoAjusteNota
) {
    private static final int TOTAL_COMPETENCIAS = 5;

    public ContextoJulia {
        exigirTexto(tema, "tema");
        exigirTexto(textoRedacao, "textoRedacao");
        if (notaTotal < 0 || notaTotal > 1000) {
            throw new IllegalArgumentException("a nota total deve estar entre 0 e 1000");
        }
        Objects.requireNonNull(competencias, "competencias é obrigatório");
        if (competencias.size() != TOTAL_COMPETENCIAS) {
            throw new IllegalArgumentException("o contexto precisa das 5 competências");
        }
        long distintas = competencias.stream().map(CompetenciaContexto::numero).distinct().count();
        if (distintas != TOTAL_COMPETENCIAS) {
            throw new IllegalArgumentException("há competências repetidas no contexto");
        }
        competencias = competencias.stream()
                .sorted(Comparator.comparingInt(CompetenciaContexto::numero))
                .toList();
        diagnostico = textoOpcional(diagnostico);
        motivoAjusteNota = textoOpcional(motivoAjusteNota);
    }

    public boolean possuiDiagnostico() {
        return diagnostico != null;
    }

    public boolean possuiAjusteNota() {
        return motivoAjusteNota != null;
    }

    /** Campos opcionais: em branco vira nulo, para o prompt não ganhar marcação vazia. */
    private static String textoOpcional(String valor) {
        return (valor == null || valor.isBlank()) ? null : valor.strip();
    }

    private static void exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
    }
}
