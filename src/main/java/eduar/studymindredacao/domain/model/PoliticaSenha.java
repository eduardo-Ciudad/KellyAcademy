package eduar.studymindredacao.domain.model;

import java.nio.charset.StandardCharsets;

/**
 * Regra única de senha, usada no cadastro, na redefinição pelo link e na troca com a conta logada.
 * <p>
 * O BCrypt só considera os primeiros 72 <b>bytes</b>: o que passar disso seria ignorado em silêncio (e as versões
 * novas do Spring Security recusam com erro). Por isso o máximo é conferido em bytes UTF-8, não em caracteres:
 * "á" ocupa 2 bytes, então uma senha cheia de acentos chega ao limite antes de 72 caracteres.
 * <p>
 * As mensagens nunca repetem a senha.
 */
public final class PoliticaSenha {
    public static final int TAMANHO_MINIMO = 8;
    public static final int BYTES_MAXIMO = 72;

    private PoliticaSenha() {
    }

    public static void validar(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO) {
            throw new IllegalArgumentException("senha deve ter pelo menos " + TAMANHO_MINIMO + " caracteres");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMO) {
            throw new IllegalArgumentException(
                    "senha muito longa: o limite é de " + BYTES_MAXIMO + " caracteres (acentos e emojis contam mais)"
            );
        }
    }
}
