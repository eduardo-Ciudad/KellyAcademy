package eduar.studymindredacao.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.RecursoIA;

/**
 * Configuração lida na subida da aplicação, com as regras que impedem a API de subir
 * com valor faltando ou inválido (SEC-14). Sem Spring aqui, para testar direto.
 * O {@link #toString()} nunca mostra usuário nem senha do e-mail.
 */
record ConfiguracaoInicial(
        int correcoesGlobais,
        int transcricoesGlobais,
        String modelo,
        String modeloTranscricao,
        boolean emailHabilitado,
        String mailHost,
        String mailPorta,
        String mailUsuario,
        String mailSenha,
        String frontendUrl
) {
    private static final Set<String> HOSTS_LOCAIS = Set.of("localhost", "127.0.0.1");

    /** Lista de problemas encontrados; vazia quando a configuração está correta. */
    List<String> validar() {
        List<String> erros = new ArrayList<>();

        positivo(correcoesGlobais, "LIMITE_CORRECOES_GLOBAL", erros);
        positivo(transcricoesGlobais, "LIMITE_TRANSCRICOES_GLOBAL", erros);
        tetoCobreOPro(correcoesGlobais, RecursoIA.CORRECAO, "LIMITE_CORRECOES_GLOBAL", erros);
        tetoCobreOPro(transcricoesGlobais, RecursoIA.TRANSCRICAO, "LIMITE_TRANSCRICOES_GLOBAL", erros);


        obrigatorio(modelo, "gemini.modelo", erros);
        if (vazio(modeloTranscricao)) {
            erros.add("GEMINI_MODELO_TRANSCRICAO não pode ficar vazia (remova a variável ou informe um modelo)");
        }

        if (emailHabilitado) {
            obrigatorio(mailHost, "MAIL_HOST", erros);
            validarPorta(erros);
            obrigatorio(mailUsuario, "MAIL_USERNAME", erros);
            obrigatorio(mailSenha, "MAIL_PASSWORD", erros);
            validarFrontendUrl(erros);
        }
        return erros;
    }

    String resumoSeguro() {
        String email = emailHabilitado
                ? "ligado (" + mailHost + ":" + mailPorta + ", links para " + frontendUrl + ")"
                : "desligado";
        return "correções: Grátis " + Plano.GRATIS.limiteDiario(RecursoIA.CORRECAO)
                + " e Pro " + Plano.PRO.limiteDiario(RecursoIA.CORRECAO) + " por aluno (teto " + correcoesGlobais + "), "
                + "transcrições: Grátis " + Plano.GRATIS.limiteDiario(RecursoIA.TRANSCRICAO)
                + " e Pro " + Plano.PRO.limiteDiario(RecursoIA.TRANSCRICAO) + " por aluno (teto " + transcricoesGlobais + "), "
                + "modelo " + modelo + ", modelo da transcrição " + modeloTranscricao + ", "
                + "e-mail " + email;
    }

    @Override
    public String toString() {
        return "ConfiguracaoInicial[" + resumoSeguro() + "]";
    }

    private void validarPorta(List<String> erros) {
        if (vazio(mailPorta)) {
            erros.add("MAIL_PORT é obrigatória com EMAIL_HABILITADO=true");
            return;
        }
        try {
            int porta = Integer.parseInt(mailPorta.strip());
            if (porta < 1 || porta > 65535) {
                erros.add("MAIL_PORT deve estar entre 1 e 65535");
            }
        } catch (NumberFormatException e) {
            erros.add("MAIL_PORT deve ser um número");
        }
    }

    private void validarFrontendUrl(List<String> erros) {
        if (vazio(frontendUrl)) {
            erros.add("APP_FRONTEND_URL é obrigatória com EMAIL_HABILITADO=true");
            return;
        }
        String url = frontendUrl.strip();
        if (url.endsWith("/")) {
            erros.add("APP_FRONTEND_URL não deve terminar com barra");
        }
        try {
            URI uri = new URI(url);
            String esquema = uri.getScheme();
            String host = uri.getHost();
            if (esquema == null || host == null) {
                erros.add("APP_FRONTEND_URL deve ser uma URL completa (ex.: https://app.exemplo.com.br)");
            } else if ("http".equalsIgnoreCase(esquema)) {
                if (!HOSTS_LOCAIS.contains(host.toLowerCase())) {
                    erros.add("APP_FRONTEND_URL deve usar https (http só em localhost): o link leva o token");
                }
            } else if (!"https".equalsIgnoreCase(esquema)) {
                erros.add("APP_FRONTEND_URL deve usar https");
            }
        } catch (URISyntaxException e) {
            erros.add("APP_FRONTEND_URL não é uma URL válida");
        }
    }

    private static void positivo(int valor, String nome, List<String> erros) {
        if (valor <= 0) {
            erros.add(nome + " deve ser maior que zero");
        }
    }

    private void obrigatorio(String valor, String nome, List<String> erros) {
        if (vazio(valor)) {
            erros.add(nome + (emailHabilitado && nome.startsWith("MAIL_")
                    ? " é obrigatória com EMAIL_HABILITADO=true"
                    : " é obrigatório"));
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }


    /** Um teto abaixo do limite do Pro barraria um único aluno Pro antes da cota dele acabar. */
    private static void tetoCobreOPro(int teto, RecursoIA recurso, String nome, List<String> erros) {
        int limitePro = Plano.PRO.limiteDiario(recurso);
        if (teto > 0 && teto < limitePro) {
            erros.add(nome + " deve ser pelo menos " + limitePro + " (limite diário do plano Pro)");
        }
    }


}
