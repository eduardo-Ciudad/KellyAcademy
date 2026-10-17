package eduar.studymindredacao.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificacaoEmailExceptionsTest {

    @Test
    void tokenInvalidoTemMensagemUnicaParaInexistenteExpiradoOuUsado() {
        assertThat(new TokenInvalidoException()).hasMessage("Link inválido ou expirado. Peça um novo link.");
    }

    @Test
    void emailNaoVerificadoOrientaOAluno() {
        assertThat(new EmailNaoVerificadoException()).hasMessageContaining("Confirme seu e-mail");
    }

    @Test
    void emailNaoVerificadoNaoEConfundidoComCredenciaisInvalidas() {
        // o handler web separa os dois: 403 com código próprio x 401
        assertThat(new EmailNaoVerificadoException()).isNotInstanceOf(CredenciaisInvalidasException.class);
        assertThat(new CredenciaisInvalidasException()).isNotInstanceOf(EmailNaoVerificadoException.class);
    }
}
