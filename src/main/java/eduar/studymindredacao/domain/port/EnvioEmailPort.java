package eduar.studymindredacao.domain.port;

import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;

public interface EnvioEmailPort {
    /**
     * Agenda o envio do e-mail de verificação. Não lança exceção por falha de SMTP: o aluno pode pedir
     * outro link, e o cadastro não pode falhar por causa do servidor de e-mail.
     */
    void enviarVerificacao(MensagemVerificacaoEmail mensagem);
}
