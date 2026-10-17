package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.domain.model.Usuario;
import eduar.studymindredacao.domain.model.enums.Role;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VerificacaoEmailDtosTest {
    private static final OffsetDateTime CRIADO = OffsetDateTime.of(2026, 10, 16, 21, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void toStringDoPedidoDeVerificacaoNaoMostraOToken() {
        assertThat(new VerificarEmailRequest("TokenSecreto123").toString())
                .doesNotContain("TokenSecreto123")
                .contains("token=***");
    }

    @Test
    void cadastroInformaSeOEmailJaEstaVerificado() {
        var naoVerificado = new Usuario(UUID.randomUUID(), "Ana", "ana@exemplo.com", "hash", Role.ALUNO, CRIADO, null);
        var verificado = naoVerificado.comEmailVerificado(CRIADO.plusMinutes(1));

        assertThat(UsuarioResponse.de(naoVerificado).emailVerificado()).isFalse();
        assertThat(UsuarioResponse.de(verificado).emailVerificado()).isTrue();
    }

    @Test
    void respostaDoCadastroNaoExpoeOHashDaSenha() {
        var usuario = new Usuario(UUID.randomUUID(), "Ana", "ana@exemplo.com", "hash-secreto", Role.ALUNO, CRIADO, null);

        assertThat(UsuarioResponse.de(usuario).toString()).doesNotContain("hash-secreto");
    }
}
