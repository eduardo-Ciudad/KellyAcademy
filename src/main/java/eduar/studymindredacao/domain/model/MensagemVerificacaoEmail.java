package eduar.studymindredacao.domain.model;

/**
 * O que o caso de uso entrega para o envio do e-mail de verificação. O link leva o token em claro,
 * por isso o {@link #toString()} não o mostra.
 */
public record MensagemVerificacaoEmail(String destinatario, String nome, String link) {
    public MensagemVerificacaoEmail {
        Validacoes.validarTextoObrigatorio(destinatario, "destinatario", 150);
        Validacoes.validarTextoObrigatorio(nome, "nome", 150);
        Validacoes.validarTextoObrigatorio(link, "link", null);
    }

    @Override
    public String toString() {
        return "MensagemVerificacaoEmail[destinatario=" + destinatario + ", nome=" + nome + ", link=***]";
    }
}
