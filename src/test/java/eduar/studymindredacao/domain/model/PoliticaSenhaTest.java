package eduar.studymindredacao.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaSenhaTest {

    @Test
    void aceitaOMinimoDe8Caracteres() {
        assertThatCode(() -> PoliticaSenha.validar("12345678")).doesNotThrowAnyException();
    }

    @Test
    void recusa7Caracteres() {
        assertThatThrownBy(() -> PoliticaSenha.validar("1234567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pelo menos 8");
    }

    @Test
    void recusaNulaEVazia() {
        assertThatThrownBy(() -> PoliticaSenha.validar(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PoliticaSenha.validar("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceitaExatamente72BytesERecusa73() {
        assertThatCode(() -> PoliticaSenha.validar("a".repeat(72))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PoliticaSenha.validar("a".repeat(73)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("muito longa");
    }

    @Test
    void oLimiteEEmBytesAcentoContaDois() {
        // "á" = 2 bytes em UTF-8: 36 cabem (72 bytes), 37 não (74 bytes) mesmo com só 37 caracteres
        assertThatCode(() -> PoliticaSenha.validar("á".repeat(36))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PoliticaSenha.validar("á".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceitaEspacosESimbolos() {
        assertThatCode(() -> PoliticaSenha.validar("minha senha #1 é boa")).doesNotThrowAnyException();
    }

    @Test
    void aMensagemNuncaRepeteASenha() {
        String curta = "Ab#7xyz";
        String longa = "segredo-" + "x".repeat(80);

        assertThatThrownBy(() -> PoliticaSenha.validar(curta)).message().doesNotContain(curta);
        assertThatThrownBy(() -> PoliticaSenha.validar(longa)).message().doesNotContain("segredo-");
    }
}
