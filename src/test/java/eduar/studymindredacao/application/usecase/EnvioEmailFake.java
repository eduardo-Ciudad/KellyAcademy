package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import eduar.studymindredacao.domain.port.EnvioEmailPort;

import java.util.ArrayList;
import java.util.List;

class EnvioEmailFake implements EnvioEmailPort {
    final List<MensagemVerificacaoEmail> enviadas = new ArrayList<>();

    @Override
    public void enviarVerificacao(MensagemVerificacaoEmail mensagem) {
        enviadas.add(mensagem);
    }

    MensagemVerificacaoEmail ultima() {
        return enviadas.get(enviadas.size() - 1);
    }

    /** O token em claro que foi no link do último e-mail. */
    String ultimoToken() {
        String link = ultima().link();
        return link.substring(link.indexOf("#token=") + "#token=".length());
    }
}
