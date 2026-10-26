package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanoDoUsuarioTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 26, 15, 0, 0, 0, ZoneOffset.UTC);
    private static final Assinatura ASSINATURA = Assinatura.nova(UUID.randomUUID(), "sub_1", CicloAssinatura.MENSAL,
            StatusAssinatura.ACTIVE, AGORA.plusMonths(1), false, AGORA);

    @Test
    void gratisNaoTemAssinaturaVigente() {
        var gratis = PlanoDoUsuario.gratis();

        assertThat(gratis.plano()).isEqualTo(Plano.GRATIS);
        assertThat(gratis.assinaturaVigente()).isNull();
    }

    @Test
    void proCarregaAAssinaturaVigente() {
        var pro = PlanoDoUsuario.pro(ASSINATURA);

        assertThat(pro.plano()).isEqualTo(Plano.PRO);
        assertThat(pro.assinaturaVigente()).isSameAs(ASSINATURA);
    }

    @Test
    void proSemAssinaturaEInvalido() {
        assertThatThrownBy(() -> new PlanoDoUsuario(Plano.PRO, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gratisComAssinaturaEInvalido() {
        assertThatThrownBy(() -> new PlanoDoUsuario(Plano.GRATIS, ASSINATURA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void planoEObrigatorio() {
        assertThatThrownBy(() -> new PlanoDoUsuario(null, null)).isInstanceOf(NullPointerException.class);
    }
}
