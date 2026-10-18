package eduar.studymindredacao.adapter.out.email;

import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * Peças comuns a todos os e-mails da Kelly Academy. Tudo que vem do aluno (o nome) é tratado como não confiável:
 * sem caracteres de controle nem quebras de linha (não forja linhas nem cabeçalhos) e escapado no HTML
 * (só &amp;, &lt;, &gt;, aspas e apóstrofo: acentos ficam como estão, o e-mail vai em UTF-8).
 * Links só com https (ou http em localhost), para nunca virar "javascript:" ou outro esquema.
 */
final class TextoEmailSeguro {
    static final int TAMANHO_MAXIMO_NOME = 60;

    private TextoEmailSeguro() {
    }

    /** Remove controles e quebras (inclusive U+2028/U+2029), junta espaços e limita o tamanho. */
    static String limparNome(String nome) {
        if (nome == null) {
            return "";
        }
        String limpo = nome.replaceAll("[\\p{Cntrl}\\u2028\\u2029]+", " ").replaceAll("\\s+", " ").strip();
        return limpo.length() > TAMANHO_MAXIMO_NOME ? limpo.substring(0, TAMANHO_MAXIMO_NOME).strip() : limpo;
    }

    /** "Olá, Ana!" ou, sem nome aproveitável, "Olá!". */
    static String saudacao(String nome) {
        String limpo = limparNome(nome);
        return limpo.isEmpty() ? "Olá!" : "Olá, " + limpo + "!";
    }

    static String validarLink(String link) {
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

    static Duration exigirValidade(Duration validade) {
        Objects.requireNonNull(validade, "validade é obrigatória");
        if (validade.isZero() || validade.isNegative()) {
            throw new IllegalArgumentException("validade deve ser positiva");
        }
        return validade;
    }

    /** "24 horas", "1 hora", "30 minutos". */
    static String descreverValidade(Duration validade) {
        exigirValidade(validade);
        long horas = validade.toHours();
        if (horas >= 1 && validade.equals(Duration.ofHours(horas))) {
            return horas == 1 ? "1 hora" : horas + " horas";
        }
        long minutos = Math.max(1, validade.toMinutes());
        return minutos == 1 ? "1 minuto" : minutos + " minutos";
    }

    static String escapar(String texto) {
        return HtmlUtils.htmlEscape(texto, "UTF-8");
    }

    // ---------- blocos de HTML (texto puro entra escapado) ----------

    static String htmlSaudacao(String saudacao) {
        return "<p style=\"margin:0 0 16px;font-size:16px;\">" + escapar(saudacao) + "</p>";
    }

    static String htmlParagrafo(String texto) {
        return "<p style=\"margin:0 0 24px;font-size:15px;line-height:1.5;\">" + escapar(texto) + "</p>";
    }

    static String htmlBotao(String linkValidado, String rotulo) {
        return "<p style=\"margin:0 0 24px;\"><a href=\"" + escapar(linkValidado) + "\" style=\"display:inline-block;"
                + "padding:12px 20px;background:#1877f2;color:#ffffff;text-decoration:none;border-radius:6px;"
                + "font-size:15px;\">" + escapar(rotulo) + "</a></p>";
    }

    static String htmlLinkReserva(String linkValidado) {
        return "<p style=\"margin:0 0 8px;font-size:13px;color:#606770;\">"
                + "Se o botão não funcionar, copie e cole este endereço no navegador:</p>\n"
                + "<p style=\"margin:0 0 24px;font-size:13px;word-break:break-all;color:#606770;\">"
                + escapar(linkValidado) + "</p>";
    }

    static String htmlNota(String texto) {
        return "<p style=\"margin:0 0 8px;font-size:13px;color:#606770;\">" + escapar(texto) + "</p>";
    }

    static String htmlNotaFinal(String texto) {
        return "<p style=\"margin:0;font-size:13px;color:#606770;\">" + escapar(texto) + "</p>";
    }

    /** Página completa: card branco centralizado, sem imagens externas nem scripts. */
    static String htmlPagina(String... blocos) {
        return """
                <!doctype html>
                <html lang="pt-BR">
                <body style="margin:0;padding:24px;background:#f0f2f5;font-family:Arial,Helvetica,sans-serif;color:#1c1e21;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;margin:0 auto;background:#ffffff;border:1px solid #dddfe2;border-radius:8px;">
                    <tr><td style="padding:32px;">
                """
                + String.join("\n", blocos)
                + """

                    </td></tr>
                  </table>
                </body>
                </html>
                """;
    }
}
