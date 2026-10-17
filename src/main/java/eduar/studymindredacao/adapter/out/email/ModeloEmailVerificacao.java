package eduar.studymindredacao.adapter.out.email;

import eduar.studymindredacao.domain.model.MensagemVerificacaoEmail;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * Monta o e-mail de verificação (texto puro + HTML). Tudo que vem do aluno (o nome) é tratado como não confiável:
 * sem caracteres de controle nem quebras de linha (não forja linhas nem cabeçalhos) e escapado no HTML
 * (só &amp;, &lt;, &gt;, aspas e apóstrofo: acentos ficam como estão, o e-mail vai em UTF-8).
 * O link só é aceito com https (ou http em localhost), para nunca virar "javascript:" ou outro esquema.
 */
public class ModeloEmailVerificacao {
    static final String ASSUNTO = "Confirme seu e-mail na Kelly Academy";
    static final int TAMANHO_MAXIMO_NOME = 60;

    private final Duration validade;

    public ModeloEmailVerificacao(Duration validade) {
        Objects.requireNonNull(validade, "validade é obrigatória");
        if (validade.isZero() || validade.isNegative()) {
            throw new IllegalArgumentException("validade deve ser positiva");
        }
        this.validade = validade;
    }

    public EmailMontado montar(MensagemVerificacaoEmail mensagem) {
        Objects.requireNonNull(mensagem, "mensagem é obrigatória");
        String link = validarLink(mensagem.link());
        String nome = limparNome(mensagem.nome());
        String saudacao = nome.isEmpty() ? "Olá!" : "Olá, " + nome + "!";
        String prazo = descreverValidade();

        String texto = saudacao + "\n\n"
                + "Para ativar sua conta na Kelly Academy, confirme seu e-mail abrindo o link abaixo:\n\n"
                + link + "\n\n"
                + "O link vale por " + prazo + " e só pode ser usado uma vez.\n\n"
                + "Se você não criou uma conta na Kelly Academy, ignore este e-mail.";

        String linkHtml = HtmlUtils.htmlEscape(link, "UTF-8");
        String html = """
                <!doctype html>
                <html lang="pt-BR">
                <body style="margin:0;padding:24px;background:#f0f2f5;font-family:Arial,Helvetica,sans-serif;color:#1c1e21;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #dddfe2;border-radius:8px;">
                    <tr><td style="padding:32px;">
                      <p style="margin:0 0 16px;font-size:16px;">%s</p>
                      <p style="margin:0 0 24px;font-size:15px;line-height:1.5;">Para ativar sua conta na Kelly Academy, confirme seu e-mail:</p>
                      <p style="margin:0 0 24px;"><a href="%s" style="display:inline-block;padding:12px 20px;background:#1877f2;color:#ffffff;text-decoration:none;border-radius:6px;font-size:15px;">Confirmar e-mail</a></p>
                      <p style="margin:0 0 8px;font-size:13px;color:#606770;">Se o botão não funcionar, copie e cole este endereço no navegador:</p>
                      <p style="margin:0 0 24px;font-size:13px;word-break:break-all;color:#606770;">%s</p>
                      <p style="margin:0 0 8px;font-size:13px;color:#606770;">O link vale por %s e só pode ser usado uma vez.</p>
                      <p style="margin:0;font-size:13px;color:#606770;">Se você não criou uma conta na Kelly Academy, ignore este e-mail.</p>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(saudacao, "UTF-8"), linkHtml, linkHtml, prazo);

        return new EmailMontado(mensagem.destinatario(), ASSUNTO, texto, html);
    }

    /** Remove controles e quebras (inclusive U+2028/U+2029), junta espaços e limita o tamanho. */
    static String limparNome(String nome) {
        if (nome == null) {
            return "";
        }
        String limpo = nome.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").replaceAll("\\s+", " ").strip();
        return limpo.length() > TAMANHO_MAXIMO_NOME ? limpo.substring(0, TAMANHO_MAXIMO_NOME).strip() : limpo;
    }

    private static String validarLink(String link) {
        String l = link == null ? "" : link.strip();
        boolean https = l.startsWith("https://");
        boolean local = l.startsWith("http://localhost:") || l.startsWith("http://localhost/")
                || l.startsWith("http://127.0.0.1:") || l.startsWith("http://127.0.0.1/");
        if (!https && !local) {
            throw new IllegalArgumentException("link do e-mail deve usar https");
        }
        if (l.chars().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))) {
            throw new IllegalArgumentException("link do e-mail não pode ter espaços ou caracteres de controle");
        }
        return l;
    }

    private String descreverValidade() {
        long horas = validade.toHours();
        if (horas >= 1 && validade.equals(Duration.ofHours(horas))) {
            return horas == 1 ? "1 hora" : horas + " horas";
        }
        long minutos = Math.max(1, validade.toMinutes());
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }
}
