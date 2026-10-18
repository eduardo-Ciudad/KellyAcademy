package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemRedefinicaoSenha;

import java.time.Duration;
import java.util.Objects;

/** E-mail com o link de redefinição de senha (texto puro + HTML). O link leva o token: nunca vai para o log. */
public class ModeloEmailRedefinicaoSenha {
    static final String ASSUNTO = "Redefinir sua senha da Kelly Academy";

    private final Duration validade;

    public ModeloEmailRedefinicaoSenha(Duration validade) {
        this.validade = TextoEmailSeguro.exigirValidade(validade);
    }

    public EmailMontado montar(MensagemRedefinicaoSenha mensagem) {
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        String link = TextoEmailSeguro.validarLink(mensagem.link());
        String saudacao = TextoEmailSeguro.saudacao(mensagem.nome());
        String prazo = "O link vale por " + TextoEmailSeguro.descreverValidade(validade) + " e só pode ser usado uma vez.";
        String pedido = "Recebemos um pedido para redefinir a senha da sua conta na Kelly Academy.";
        String naoFoiVoce = "Se você não pediu, ignore este e-mail: sua senha continua a mesma.";

        String texto = saudacao + "\n\n"
                + pedido + " Para criar uma senha nova, abra o link abaixo:\n\n"
                + link + "\n\n"
                + prazo + "\n\n"
                + naoFoiVoce;

        String html = TextoEmailSeguro.htmlPagina(
                TextoEmailSeguro.htmlSaudacao(saudacao),
                TextoEmailSeguro.htmlParagrafo(pedido),
                TextoEmailSeguro.htmlBotao(link, "Redefinir senha"),
                TextoEmailSeguro.htmlLinkReserva(link),
                TextoEmailSeguro.htmlNota(prazo),
                TextoEmailSeguro.htmlNotaFinal(naoFoiVoce)
        );

        return new EmailMontado(mensagem.destinatario(), ASSUNTO, texto, html);
    }
}
