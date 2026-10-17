package eduar.studymindredacao.domain.model;

import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioTest {
    private static final OffsetDateTime CRIADO = OffsetDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.ofHours(-3));
    private static final OffsetDateTime VERIFICADO = CRIADO.plusMinutes(5);

    private static Usuario naoVerificado() {
        return new Usuario(UUID.randomUUID(), "Ana", "ana@exemplo.com", "hash-bcrypt", Role.ALUNO, CRIADO, null);
    }

    @Test
    void semDataDeVerificacaoOEmailNaoEstaVerificado() {
        assertThat(naoVerificado().emailVerificado()).isFalse();
    }

    @Test
    void comEmailVerificadoGuardaADataEMantemORestante() {
        var original = naoVerificado();

        var verificado = original.comEmailVerificado(VERIFICADO);

        assertThat(verificado.emailVerificado()).isTrue();
        assertThat(verificado.emailVerificadoEm()).isEqualTo(VERIFICADO);
        assertThat(verificado.id()).isEqualTo(original.id());
        assertThat(verificado.nome()).isEqualTo("Ana");
        assertThat(verificado.email()).isEqualTo("ana@exemplo.com");
        assertThat(verificado.senhaHash()).isEqualTo("hash-bcrypt");
        assertThat(verificado.role()).isEqualTo(Role.ALUNO);
        assertThat(verificado.criadoEm()).isEqualTo(CRIADO);
        assertThat(original.emailVerificado()).as("o original não muda").isFalse();
    }

    @Test
    void verificarDeNovoMantemADataDaPrimeiraConfirmacao() {
        var verificado = naoVerificado().comEmailVerificado(VERIFICADO);

        var deNovo = verificado.comEmailVerificado(VERIFICADO.plusDays(1));

        assertThat(deNovo.emailVerificadoEm()).isEqualTo(VERIFICADO);
    }

    @Test
    void exigeADataDaVerificacao() {
        assertThatThrownBy(() -> naoVerificado().comEmailVerificado(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("quando");
    }

    @Test
    void continuaNormalizandoEmailEAssumindoAluno() {
        var usuario = new Usuario(null, "Ana", "  Ana@Exemplo.COM ", "hash", null, null, null);

        assertThat(usuario.email()).isEqualTo("ana@exemplo.com");
        assertThat(usuario.role()).isEqualTo(Role.ALUNO);
    }

    @Test
    void toStringNaoMostraOHashDaSenha() {
        assertThat(naoVerificado().toString()).doesNotContain("hash-bcrypt").contains("senhaHash=***");
    }
}
