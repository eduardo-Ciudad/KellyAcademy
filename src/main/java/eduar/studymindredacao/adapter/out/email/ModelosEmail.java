package eduar.studymindredacao.adapter.out.email;

import java.util.Objects;

/** Os modelos de todos os e-mails que o adapter SMTP sabe montar. */
public record ModelosEmail(
        ModeloEmailVerificacao verificacao,
        ModeloEmailRedefinicaoSenha redefinicaoSenha,
        ModeloEmailSenhaAlterada senhaAlterada
) {
    public ModelosEmail {
        Objects.requireNonNull(verificacao, "modelo de verificação é obrigatório");
        Objects.requireNonNull(redefinicaoSenha, "modelo de redefinição de senha é obrigatório");
        Objects.requireNonNull(senhaAlterada, "modelo de senha alterada é obrigatório");
    }
}
