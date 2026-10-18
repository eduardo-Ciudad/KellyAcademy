package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.MensagemRedefinicaoSenha;
import eduar.studymindredacao.domain.model.MensagemSenhaAlterada;
import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;

/**
 * Envio de e-mails transacionais. Nenhum método lança exceção por falha de SMTP: o aluno pode pedir outro link,
 * e o fluxo que disparou o e-mail (cadastro, troca de senha) não pode falhar por causa do servidor de e-mail.
 * Com transação aberta, o envio só sai depois do commit.
 */
public interface EnvioEmailPort {
    /** Link de confirmação do e-mail, enviado no cadastro e no reenvio. */
    void enviarVerificacao(MensagemVerificacaoEmail mensagem);

    /** Link de redefinição de senha ("Esqueci minha senha"). */
    void enviarRedefinicaoSenha(MensagemRedefinicaoSenha mensagem);

    /** Aviso de que a senha foi trocada, sem token. */
    void enviarAvisoSenhaAlterada(MensagemSenhaAlterada mensagem);
}
