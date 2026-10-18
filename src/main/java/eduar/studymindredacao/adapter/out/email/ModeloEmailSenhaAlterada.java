package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemSenhaAlterada;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Aviso de que a senha foi trocada, para o dono perceber se não foi ele. Não leva token: o botão abre a página
 * "Esqueci minha senha", onde ele pede um link novo. A data sai sempre no horário de Brasília.
 */
public class ModeloEmailSenhaAlterada {
    static final String ASSUNTO = "Sua senha da Kelly Academy foi alterada";
    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter FORMATO =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"));

    public EmailMontado montar(MensagemSenhaAlterada mensagem) {
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        String link = TextoEmailSeguro.validarLink(mensagem.linkEsqueciSenha());
        String saudacao = TextoEmailSeguro.saudacao(mensagem.nome());
        String quando = mensagem.alteradaEm().atZoneSameInstant(FUSO).format(FORMATO);
        String alterada = "A senha da sua conta na Kelly Academy foi alterada em " + quando + " (horário de Brasília).";
        String sessoes = "Por segurança, os aparelhos que estavam conectados com a senha antiga foram desconectados.";
        String naoFoiVoce = "Não foi você? Redefina sua senha agora:";
        String foiVoce = "Se foi você, não precisa fazer nada.";

        String texto = saudacao + "\n\n"
                + alterada + "\n\n"
                + sessoes + "\n\n"
                + naoFoiVoce + "\n\n"
                + link + "\n\n"
                + foiVoce;

        String html = TextoEmailSeguro.htmlPagina(
                TextoEmailSeguro.htmlSaudacao(saudacao),
                TextoEmailSeguro.htmlParagrafo(alterada),
                TextoEmailSeguro.htmlParagrafo(sessoes),
                TextoEmailSeguro.htmlParagrafo(naoFoiVoce),
                TextoEmailSeguro.htmlBotao(link, "Redefinir minha senha"),
                TextoEmailSeguro.htmlLinkReserva(link),
                TextoEmailSeguro.htmlNotaFinal(foiVoce)
        );

        return new EmailMontado(mensagem.destinatario(), ASSUNTO, texto, html);
    }
}
