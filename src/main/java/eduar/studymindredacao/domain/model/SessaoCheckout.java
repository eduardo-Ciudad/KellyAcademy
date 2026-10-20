package eduar.studymindredacao.domain.model;

public record SessaoCheckout(String id, String url) {
    static final String PREFIXO_SESSAO = "cs_";

    public SessaoCheckout {
        Validacoes.validarTextoObrigatorio(id, "id", 255);
        if (!id.startsWith(PREFIXO_SESSAO)) {
            throw new IllegalArgumentException("id da sessão deve começar com " + PREFIXO_SESSAO);
        }
        Validacoes.validarTextoObrigatorio(url, "url", 2048);
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("url da sessão deve ser https");
        }
    }
}