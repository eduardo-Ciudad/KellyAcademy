package eduar.studymindredacao.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public final class Validacoes {
    /** Identificador estável em URL e filtros: minúsculas, números e hífen (ex.: saude-mental). */
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
    /** SHA-256 em hexadecimal minúsculo: sempre 64 caracteres. */
    private static final Pattern HASH_SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private Validacoes() {
    }

    public static void validarTextoObrigatorio(String valor, String campo, Integer tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        validarTamanhoMaximo(valor, campo, tamanhoMaximo);
    }

    public static <T> T requererNaoNulo(T valor, String campo) {
        return Objects.requireNonNull(valor, campo + " é obrigatório");
    }

    public static void validarTamanhoMaximo(String valor, String campo, Integer tamanhoMaximo) {
        if (valor != null && tamanhoMaximo != null && valor.length() > tamanhoMaximo) {
            throw new IllegalArgumentException(
                    campo + " deve ter no máximo " + tamanhoMaximo + " caracteres"
            );
        }
    }

    public static void validarSlug(String valor, String campo, int tamanhoMaximo) {
        validarTextoObrigatorio(valor, campo, tamanhoMaximo);
        if (!SLUG.matcher(valor).matches()) {
            throw new IllegalArgumentException(campo + " deve ter só letras minúsculas, números e hífen");
        }
    }

    public static void validarHashSha256(String valor, String campo) {
        if (valor == null || !HASH_SHA256.matcher(valor).matches()) {
            throw new IllegalArgumentException(campo + " deve ser um SHA-256 em hexadecimal minúsculo");
        }
    }
}
