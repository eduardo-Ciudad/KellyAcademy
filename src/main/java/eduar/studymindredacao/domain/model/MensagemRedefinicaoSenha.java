package eduar.studymindredacao.domain.model;

/**
 * O que o caso de uso entrega para o envio do e-mail de redefinição de senha. O link leva o token em claro,
 * por isso o {@link #toString()} não o mostra.
 */
public record MensagemRedefinicaoSenha(String destinatario, String nome, String link) {
    public MensagemRedefinicaoSenha {
        Validacoes.validarTextoObrigatorio(destinatario, "destinatario", 150);
        Validacoes.validarTextoObrigatorio(nome, "nome", 150);
        Validacoes.validarTextoObrigatorio(link, "link", null);
    }

    @Override
    public String toString() {
        return "MensagemRedefinicaoSenha[destinatario=" + destinatario + ", nome=" + nome + ", link=***]";
    }
}
