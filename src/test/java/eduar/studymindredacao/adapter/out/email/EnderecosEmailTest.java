package eduar.studymindredacao.adapter.out.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnderecosEmailTest {

    @Test
    void escondeAParteAntesDoArroba() {
        assertThat(EnderecosEmail.mascarar("ana.souza@gmail.com")).isEqualTo("***@gmail.com");
    }

    @Test
    void semArrobaOuNuloViraSoAsteriscos() {
        assertThat(EnderecosEmail.mascarar(null)).isEqualTo("***");
        assertThat(EnderecosEmail.mascarar("sem-arroba")).isEqualTo("***");
        assertThat(EnderecosEmail.mascarar("termina@")).isEqualTo("***");
    }

    @Test
    void removeQuebrasDeLinhaParaNaoForjarLog() {
        assertThat(EnderecosEmail.mascarar("a@gmail.com\nFALSO")).isEqualTo("***@gmail.comFALSO");
    }
}
