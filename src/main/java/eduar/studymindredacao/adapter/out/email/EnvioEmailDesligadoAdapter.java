package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import eduar.studymindredacao.domain.port.EnvioEmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Usado com EMAIL_HABILITADO=false (local, CI, piloto sem SMTP). Não envia nada e nunca registra o link:
 * nesse modo a conta já nasce verificada, então não há link a recuperar.
 */
public class EnvioEmailDesligadoAdapter implements EnvioEmailPort {
    private static final Logger log = LoggerFactory.getLogger(EnvioEmailDesligadoAdapter.class);

    @Override
    public void enviarVerificacao(MensagemVerificacaoEmail mensagem) {
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        log.info("EMAIL_HABILITADO=false: e-mail de verificação para {} não enviado",
                EnderecosEmail.mascarar(mensagem.destinatario()));
    }
}
