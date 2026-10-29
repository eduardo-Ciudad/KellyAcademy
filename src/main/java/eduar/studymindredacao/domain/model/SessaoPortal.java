package eduar.studymindredacao.domain.model;

/** Sessão do portal do cliente do Stripe (cancelar, trocar cartão, ver faturas). Vale só para um acesso. */
public record SessaoPortal(String id, String url) {
    static final String PREFIXO_SESSAO = "bps_";

    public SessaoPortal {
        Validacoes.validarTextoObrigatorio(id, "id", 255);
        if (!id.startsWith(PREFIXO_SESSAO)) {
            throw new IllegalArgumentException("id da sessão do portal deve começar com " + PREFIXO_SESSAO);
        }
        Validacoes.validarTextoObrigatorio(url, "url", 2048);
        if (!url.startsWith("https://")) {
            throw new IllegalArgumentException("url da sessão do portal deve ser https");
        }
    }
}
