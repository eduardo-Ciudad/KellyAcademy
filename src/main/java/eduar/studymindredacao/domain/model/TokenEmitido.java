package eduar.studymindredacao.domain.model;

/**
 * Token recém-gerado: o valor em claro vai só no link do e-mail; no banco fica apenas o hash.
 * O {@link #toString()} não mostra nenhum dos dois, para o token nunca parar em log.
 */
public record TokenEmitido(String valorEmClaro, String hash) {
    public TokenEmitido {
        Validacoes.validarTextoObrigatorio(valorEmClaro, "valorEmClaro", null);
        Validacoes.validarHashSha256(hash, "hash");
    }

    @Override
    public String toString() {
        return "TokenEmitido[valorEmClaro=***, hash=***]";
    }
}
