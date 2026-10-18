package eduar.studymindredacao.domain.model.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TipoTokenUsuarioTest {

    @Test
    void temOsDoisTiposComOsNomesGravadosNoBanco() {
        // os nomes vão para a coluna tokens_usuario.tipo e para o CHECK da migration: não renomear
        assertThat(TipoTokenUsuario.values())
                .extracting(Enum::name)
                .containsExactly("VERIFICACAO_EMAIL", "REDEFINICAO_SENHA");
    }
}
