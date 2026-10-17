package eduar.studymindredacao.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensagemVerificacaoEmailTest {
    private static final String LINK = "https://kellyacademy.com.br/verificar-email#token=segredo123";

    @Test
    void guardaOsDados() {
        var mensagem = new MensagemVerificacaoEmail("aluno@exemplo.com", "Ana", LINK);

        assertThat(mensagem.destinatario()).isEqualTo("aluno@exemplo.com");
        assertThat(mensagem.nome()).isEqualTo("Ana");
        assertThat(mensagem.link()).isEqualTo(LINK);
    }

    @Test
    void toStringNaoMostraOLinkComOToken() {
        String texto = new MensagemVerificacaoEmail("aluno@exemplo.com", "Ana", LINK).toString();

        assertThat(texto).doesNotContain("segredo123").contains("link=***");
    }

    @Test
    void exigeDestinatarioNomeELink() {
        assertThatThrownBy(() -> new MensagemVerificacaoEmail(" ", "Ana", LINK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("destinatario");
        assertThatThrownBy(() -> new MensagemVerificacaoEmail("aluno@exemplo.com", null, LINK))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nome");
        assertThatThrownBy(() -> new MensagemVerificacaoEmail("aluno@exemplo.com", "Ana", ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("link");
    }

    @Test
    void recusaDestinatarioAcimaDoTamanhoDaColuna() {
        String longo = "a".repeat(140) + "@exemplo.com";

        assertThatThrownBy(() -> new MensagemVerificacaoEmail(longo, "Ana", LINK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
