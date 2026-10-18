package eduar.studymindredacao.domain.model;

import java.time.OffsetDateTime;

/**
 * Aviso de que a senha foi trocada, para o dono perceber se não foi ele. Não leva token: o link aponta para a
 * página "Esqueci minha senha", onde ele pede um link novo.
 */
public record MensagemSenhaAlterada(String destinatario, String nome, OffsetDateTime alteradaEm, String linkEsqueciSenha) {
    public MensagemSenhaAlterada {
        Validacoes.validarTextoObrigatorio(destinatario, "destinatario", 150);
        Validacoes.validarTextoObrigatorio(nome, "nome", 150);
        Validacoes.requererNaoNulo(alteradaEm, "alteradaEm");
        Validacoes.validarTextoObrigatorio(linkEsqueciSenha, "linkEsqueciSenha", null);
    }
}
