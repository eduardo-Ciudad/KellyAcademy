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
    private static final OffsetDateTime ALTERADA = CRIADO.plusDays(2);

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

    @Test
    void construtorCurtoComecaNaVersaoZeroSemTrocaDeSenha() {
        var usuario = naoVerificado();

        assertThat(usuario.versaoSessao()).isZero();
        assertThat(usuario.senhaAlteradaEm()).isNull();
    }

    @Test
    void recusaVersaoDeSessaoNegativa() {
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), "Ana", "ana@exemplo.com", "hash", Role.ALUNO,
                CRIADO, null, -1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("versaoSessao");
    }

    @Test
    void comEmailVerificadoPreservaVersaoEDataDaTroca() {
        var trocou = naoVerificado().comSenhaAlterada("hash-novo", ALTERADA).comSenhaAlterada("hash-3", ALTERADA);
        var semVerificar = new Usuario(trocou.id(), "Ana", "ana@exemplo.com", "hash-3", Role.ALUNO, CRIADO,
                null, trocou.versaoSessao(), trocou.senhaAlteradaEm());

        var verificado = semVerificar.comEmailVerificado(VERIFICADO);

        assertThat(verificado.versaoSessao()).isEqualTo(2);
        assertThat(verificado.senhaAlteradaEm()).isEqualTo(ALTERADA);
    }

    @Test
    void comSenhaAlteradaTrocaOHashSobeAVersaoEGuardaAData() {
        var original = naoVerificado().comEmailVerificado(VERIFICADO);

        var alterado = original.comSenhaAlterada("hash-novo", ALTERADA);

        assertThat(alterado.senhaHash()).isEqualTo("hash-novo");
        assertThat(alterado.versaoSessao()).isEqualTo(1);
        assertThat(alterado.senhaAlteradaEm()).isEqualTo(ALTERADA);
        assertThat(alterado.id()).isEqualTo(original.id());
        assertThat(alterado.email()).isEqualTo("ana@exemplo.com");
        assertThat(alterado.criadoEm()).isEqualTo(CRIADO);
        assertThat(original.senhaHash()).as("o original não muda").isEqualTo("hash-bcrypt");
        assertThat(original.versaoSessao()).isZero();
    }

    @Test
    void cadaTrocaDeSenhaSobeAVersaoDeNovo() {
        var duasVezes = naoVerificado()
                .comSenhaAlterada("hash-2", ALTERADA)
                .comSenhaAlterada("hash-3", ALTERADA.plusMinutes(1));

        assertThat(duasVezes.versaoSessao()).isEqualTo(2);
        assertThat(duasVezes.senhaAlteradaEm()).isEqualTo(ALTERADA.plusMinutes(1));
    }

    @Test
    void trocarASenhaConfirmaOEmailQueAindaNaoEstavaVerificado() {
        var alterado = naoVerificado().comSenhaAlterada("hash-novo", ALTERADA);

        assertThat(alterado.emailVerificado()).isTrue();
        assertThat(alterado.emailVerificadoEm()).isEqualTo(ALTERADA);
    }

    @Test
    void trocarASenhaMantemADataDeVerificacaoQueJaExistia() {
        var alterado = naoVerificado().comEmailVerificado(VERIFICADO).comSenhaAlterada("hash-novo", ALTERADA);

        assertThat(alterado.emailVerificadoEm()).isEqualTo(VERIFICADO);
    }

    @Test
    void trocarASenhaExigeHashEData() {
        var usuario = naoVerificado();

        assertThatThrownBy(() -> usuario.comSenhaAlterada(" ", ALTERADA))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("novoSenhaHash");
        assertThatThrownBy(() -> usuario.comSenhaAlterada("hash-novo", null))
                .isInstanceOf(NullPointerException.class).hasMessageContaining("quando");
    }

    @Test
    void toStringMostraAVersaoMasNaoOHash() {
        String texto = naoVerificado().comSenhaAlterada("hash-novo-secreto", ALTERADA).toString();

        assertThat(texto).contains("versaoSessao=1").doesNotContain("hash-novo-secreto");
    }
}
