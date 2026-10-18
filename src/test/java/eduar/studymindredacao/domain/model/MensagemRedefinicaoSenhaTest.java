package eduar.studymindredacao.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensagemRedefinicaoSenhaTest {
    private static final String LINK = "https://kellyacademy.com.br/redefinir-senha#token=segredo123";

    @Test
    void guardaOsDados() {
        var mensagem = new MensagemRedefinicaoSenha("aluno@exemplo.com", "Ana", LINK);

        assertThat(mensagem.destinatario()).isEqualTo("aluno@exemplo.com");
        assertThat(mensagem.nome()).isEqualTo("Ana");
        assertThat(mensagem.link()).isEqualTo(LINK);
    }

    @Test
    void toStringNaoMostraOLinkComOToken() {
        String texto = new MensagemRedefinicaoSenha("aluno@exemplo.com", "Ana", LINK).toString();

        assertThat(texto).doesNotContain("segredo123").contains("link=***");
    }

    @Test
    void exigeDestinatarioNomeELink() {
        assertThatThrownBy(() -> new MensagemRedefinicaoSenha(" ", "Ana", LINK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("destinatario");
        assertThatThrownBy(() -> new MensagemRedefinicaoSenha("aluno@exemplo.com", null, LINK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nome");
        assertThatThrownBy(() -> new MensagemRedefinicaoSenha("aluno@exemplo.com", "Ana", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("link");
    }

    @Test
    void recusaDestinatarioAcimaDoTamanhoDaColuna() {
        String longo = "a".repeat(140) + "@exemplo.com";

        assertThatThrownBy(() -> new MensagemRedefinicaoSenha(longo, "Ana", LINK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
