package eduar.studymindredacao.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Chaves do Gemini, uma por projeto do Google Cloud: correção, Júlia e transcrição.
 * Cada recurso tem projeto próprio para isolar cota, custo e revogação. Se duas variáveis
 * receberem a mesma chave, esse isolamento some sem aviso, então a API não sobe.
 * Fica separado do {@link ConfiguracaoInicial} para o segredo circular pelo menor número de lugares.
 * O {@link #toString()} nunca mostra os valores.
 */
record ChavesGemini(String correcao, String julia, String transcricao) {
    static final String VAR_CORRECAO = "GEMINI_API_KEY";
    static final String VAR_JULIA = "JULIA_GEMINI_API_KEY";
    static final String VAR_TRANSCRICAO = "TRANSCRICAO_GEMINI_API_KEY";

    /** Lista de problemas encontrados; vazia quando as três chaves existem e são diferentes entre si. */
    List<String> validar() {
        List<String> erros = new ArrayList<>();
        boolean temCorrecao = obrigatoria(correcao, VAR_CORRECAO, erros);
        boolean temJulia = obrigatoria(julia, VAR_JULIA, erros);
        boolean temTranscricao = obrigatoria(transcricao, VAR_TRANSCRICAO, erros);

        if (temCorrecao && temJulia) {
            exigirDiferentes(correcao, VAR_CORRECAO, julia, VAR_JULIA, erros);
        }
        if (temCorrecao && temTranscricao) {
            exigirDiferentes(correcao, VAR_CORRECAO, transcricao, VAR_TRANSCRICAO, erros);
        }
        if (temJulia && temTranscricao) {
            exigirDiferentes(julia, VAR_JULIA, transcricao, VAR_TRANSCRICAO, erros);
        }
        return erros;
    }

    @Override
    public String toString() {
        return "ChavesGemini[correção " + estado(correcao)
                + ", Júlia " + estado(julia)
                + ", transcrição " + estado(transcricao) + "]";
    }

    /**
     * Compara ignorando espaços nas pontas (chave colada com espaço no .env não conta como diferente).
     * {@link MessageDigest#isEqual} leva o mesmo tempo qualquer que seja o conteúdo: é o padrão para comparar segredos.
     */
    static boolean iguais(String a, String b) {
        return MessageDigest.isEqual(
                a.strip().getBytes(StandardCharsets.UTF_8),
                b.strip().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void exigirDiferentes(String a, String nomeA, String b, String nomeB, List<String> erros) {
        if (iguais(a, b)) {
            erros.add(nomeB + " é igual à " + nomeA
                    + ": cada recurso precisa da chave do próprio projeto no Google Cloud");
        }
    }

    private static boolean obrigatoria(String valor, String nome, List<String> erros) {
        if (vazio(valor)) {
            erros.add(nome + " é obrigatória");
            return false;
        }
        return true;
    }

    private static String estado(String valor) {
        return vazio(valor) ? "ausente" : "configurada";
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
