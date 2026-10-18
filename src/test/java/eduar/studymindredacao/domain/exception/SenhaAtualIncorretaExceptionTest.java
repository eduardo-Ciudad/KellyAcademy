package eduar.studymindredacao.domain.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SenhaAtualIncorretaExceptionTest {

    @Test
    void temMensagemParaOAluno() {
        assertThat(new SenhaAtualIncorretaException()).hasMessage("Senha atual incorreta.");
    }

    @Test
    void naoEConfundidaComCredenciaisInvalidas() {
        // o handler web separa: 400 (aluno continua logado) x 401 (front encerra a sessão)
        assertThat(new SenhaAtualIncorretaException()).isNotInstanceOf(CredenciaisInvalidasException.class);
    }
}
