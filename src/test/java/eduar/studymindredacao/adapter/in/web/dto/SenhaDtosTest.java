package eduar.studymindredacao.adapter.in.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SenhaDtosTest {

    @Test
    void redefinirNaoMostraTokenNemSenha() {
        assertThat(new RedefinirSenhaRequest("TokenSecreto123", "SenhaSecreta456").toString())
                .doesNotContain("TokenSecreto123")
                .doesNotContain("SenhaSecreta456")
                .contains("token=***")
                .contains("novaSenha=***");
    }

    @Test
    void alterarNaoMostraNenhumaDasSenhas() {
        assertThat(new AlterarSenhaRequest("SenhaAtual123", "SenhaNova456").toString())
                .doesNotContain("SenhaAtual123")
                .doesNotContain("SenhaNova456")
                .contains("senhaAtual=***");
    }

    @Test
    void esqueciSenhaGuardaOEmail() {
        assertThat(new EsqueciSenhaRequest("ana@exemplo.com").email()).isEqualTo("ana@exemplo.com");
    }
}
