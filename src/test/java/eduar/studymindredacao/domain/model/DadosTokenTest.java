package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DadosTokenTest {
    private static final UUID ID = UUID.randomUUID();

    @Test
    void guardaAVersaoDaSessao() {
        var dados = new DadosToken(ID, "ana@exemplo.com", Role.ALUNO, 3);

        assertThat(dados.usuarioId()).isEqualTo(ID);
        assertThat(dados.versaoSessao()).isEqualTo(3);
    }

    @Test
    void tokenSemVersaoContaComoZero() {
        assertThat(new DadosToken(ID, "ana@exemplo.com", Role.ALUNO).versaoSessao()).isZero();
    }

    @Test
    void recusaVersaoNegativa() {
        assertThatThrownBy(() -> new DadosToken(ID, "ana@exemplo.com", Role.ALUNO, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versaoSessao");
    }

    @Test
    void continuaExigindoIdEmailERole() {
        assertThatThrownBy(() -> new DadosToken(null, "ana@exemplo.com", Role.ALUNO, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DadosToken(ID, " ", Role.ALUNO, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DadosToken(ID, "ana@exemplo.com", null, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
