package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.MensagemRedefinicaoSenha;
import eduar.studymindredacao.domain.model.MensagemSenhaAlterada;
import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import eduar.studymindredacao.domain.port.EnvioEmailPort;

import java.util.ArrayList;
import java.util.List;

class EnvioEmailFake implements EnvioEmailPort {
    private static final String MARCA_TOKEN = "#token=";

    final List<MensagemVerificacaoEmail> enviadas = new ArrayList<>();
    final List<MensagemRedefinicaoSenha> redefinicoes = new ArrayList<>();
    final List<MensagemSenhaAlterada> avisos = new ArrayList<>();

    @Override
    public void enviarVerificacao(MensagemVerificacaoEmail mensagem) {
        enviadas.add(mensagem);
    }

    @Override
    public void enviarRedefinicaoSenha(MensagemRedefinicaoSenha mensagem) {
        redefinicoes.add(mensagem);
    }

    @Override
    public void enviarAvisoSenhaAlterada(MensagemSenhaAlterada mensagem) {
        avisos.add(mensagem);
    }

    MensagemVerificacaoEmail ultima() {
        return enviadas.get(enviadas.size() - 1);
    }

    /** O token em claro que foi no link do último e-mail de verificação. */
    String ultimoToken() {
        return tokenDo(ultima().link());
    }

    MensagemRedefinicaoSenha ultimaRedefinicao() {
        return redefinicoes.get(redefinicoes.size() - 1);
    }

    /** O token em claro que foi no link do último e-mail de redefinição de senha. */
    String ultimoTokenRedefinicao() {
        return tokenDo(ultimaRedefinicao().link());
    }

    MensagemSenhaAlterada ultimoAviso() {
        return avisos.get(avisos.size() - 1);
    }

    private static String tokenDo(String link) {
        return link.substring(link.indexOf(MARCA_TOKEN) + MARCA_TOKEN.length());
    }
}
