package eduar.studymindredacao.adapter.in.web.dto.admin;

/** Valores aceitos no parâmetro {@code email} da listagem de usuários (o mesmo texto que o front manda). */
public enum FiltroEmailAdmin {
    VERIFICADO,
    PENDENTE;

    public Boolean comoVerificado() {
        return this == VERIFICADO;
    }
}
