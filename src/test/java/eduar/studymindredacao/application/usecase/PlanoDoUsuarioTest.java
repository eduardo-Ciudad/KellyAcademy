package eduar.studymindredacao.application.usecase;

import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.ConcessaoPro;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.OrigemPlano;
import eduar.studymindredacao.domain.model.enums.Plano;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    // ---------- concessão manual ----------

    private static final ConcessaoPro CONCESSAO = ConcessaoPro.conceder(UUID.randomUUID(), UUID.randomUUID(),
            "Testador do piloto", 30, AGORA, ZoneId.of("America/Sao_Paulo"));

    @Test
    void gratisNaoTemOrigemNemConcessao() {
        var gratis = PlanoDoUsuario.gratis();

        assertThat(gratis.origem()).isNull();
        assertThat(gratis.concessaoVigente()).isNull();
    }

    @Test
    void proPorAssinaturaTemOrigemAssinatura() {
        assertThat(PlanoDoUsuario.pro(ASSINATURA).origem()).isEqualTo(OrigemPlano.ASSINATURA);
    }

    @Test
    void proPorConcessaoCarregaAConcessaoETemOrigemConcessao() {
        var pro = PlanoDoUsuario.proPorConcessao(CONCESSAO);

        assertThat(pro.plano()).isEqualTo(Plano.PRO);
        assertThat(pro.concessaoVigente()).isSameAs(CONCESSAO);
        assertThat(pro.assinaturaVigente()).isNull();
        assertThat(pro.origem()).isEqualTo(OrigemPlano.CONCESSAO);
    }

    @Test
    void comAssinaturaEConcessaoAOrigemEAAssinatura() {
        var pro = new PlanoDoUsuario(Plano.PRO, ASSINATURA, CONCESSAO);

        assertThat(pro.origem()).isEqualTo(OrigemPlano.ASSINATURA);
    }

    @Test
    void gratisComConcessaoEInvalido() {
        assertThatThrownBy(() -> new PlanoDoUsuario(Plano.GRATIS, null, CONCESSAO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proSemAssinaturaESemConcessaoEInvalido() {
        assertThatThrownBy(() -> new PlanoDoUsuario(Plano.PRO, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fabricasRecusamNulo() {
        assertThatThrownBy(() -> PlanoDoUsuario.pro(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PlanoDoUsuario.proPorConcessao(null)).isInstanceOf(NullPointerException.class);
    }
}
