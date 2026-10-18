package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemRedefinicaoSenha;
import eduar.studymindredacao.domain.model.MensagemSenhaAlterada;
import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import eduar.studymindredacao.domain.port.EnvioEmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Usado com EMAIL_HABILITADO=false (local, CI, piloto sem SMTP). Não envia nada e nunca registra o link:
 * nesse modo a conta já nasce verificada e o "esqueci minha senha" não manda link (só fica o registro no log).
 */
public class EnvioEmailDesligadoAdapter implements EnvioEmailPort {
    private static final Logger log = LoggerFactory.getLogger(EnvioEmailDesligadoAdapter.class);

    @Override
    public void enviarVerificacao(MensagemVerificacaoEmail mensagem) {
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        registrar("verificação", mensagem.destinatario());
    }

    @Override
    public void enviarRedefinicaoSenha(MensagemRedefinicaoSenha mensagem) {
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        registrar("redefinição de senha", mensagem.destinatario());
    }

    @Override
    public void enviarAvisoSenhaAlterada(MensagemSenhaAlterada mensagem) {
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        registrar("aviso de senha alterada", mensagem.destinatario());
    }

    private static void registrar(String tipo, String destinatario) {
        log.info("EMAIL_HABILITADO=false: e-mail de {} para {} não enviado", tipo, EnderecosEmail.mascarar(destinatario));
    }
}
