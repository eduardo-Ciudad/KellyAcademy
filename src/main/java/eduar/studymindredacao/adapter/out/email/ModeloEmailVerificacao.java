package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;

import java.time.Duration;
import java.util.Objects;

/** Monta o e-mail de verificação (texto puro + HTML). As regras de segurança ficam em {@link TextoEmailSeguro}. */
public class ModeloEmailVerificacao {
    static final String ASSUNTO = "Confirme seu e-mail na Kelly Academy";
    static final int TAMANHO_MAXIMO_NOME = TextoEmailSeguro.TAMANHO_MAXIMO_NOME;

    private final Duration validade;

    public ModeloEmailVerificacao(Duration validade) {
        this.validade = TextoEmailSeguro.exigirValidade(validade);
    }

    public EmailMontado montar(MensagemVerificacaoEmail mensagem) {
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        String link = TextoEmailSeguro.validarLink(mensagem.link());
        String saudacao = TextoEmailSeguro.saudacao(mensagem.nome());
        String prazo = TextoEmailSeguro.descreverValidade(validade);

        String texto = saudacao + "\n\n"
                + "Para ativar sua conta na Kelly Academy, confirme seu e-mail abrindo o link abaixo:\n\n"
                + link + "\n\n"
                + "O link vale por " + prazo + " e só pode ser usado uma vez.\n\n"
                + "Se você não criou uma conta na Kelly Academy, ignore este e-mail.";

        String html = TextoEmailSeguro.htmlPagina(
                TextoEmailSeguro.htmlSaudacao(saudacao),
                TextoEmailSeguro.htmlParagrafo("Para ativar sua conta na Kelly Academy, confirme seu e-mail:"),
                TextoEmailSeguro.htmlBotao(link, "Confirmar e-mail"),
                TextoEmailSeguro.htmlLinkReserva(link),
                TextoEmailSeguro.htmlNota("O link vale por " + prazo + " e só pode ser usado uma vez."),
                TextoEmailSeguro.htmlNotaFinal("Se você não criou uma conta na Kelly Academy, ignore este e-mail.")
        );

        return new EmailMontado(mensagem.destinatario(), ASSUNTO, texto, html);
    }

    /** Mantido para os testes que já existem: a regra mora em {@link TextoEmailSeguro#limparNome}. */
    static String limparNome(String nome) {
        return TextoEmailSeguro.limparNome(nome);
    }
}
