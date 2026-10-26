package eduar.studymindredacao.adapter.in.web.dto;

import eduar.studymindredacao.application.usecase.PlanoDoUsuario;
import eduar.studymindredacao.domain.model.Assinatura;
import eduar.studymindredacao.domain.model.enums.CicloAssinatura;
import eduar.studymindredacao.domain.model.enums.StatusAssinatura;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlanoResponseTest {
    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 10, 26, 15, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FIM = AGORA.plusYears(1);

    @Test
    void gratisTemLimitesDoGratisNenhumRecursoESemAssinatura() {
        var resposta = PlanoResponse.de(PlanoDoUsuario.gratis());

        assertThat(resposta.plano()).isEqualTo("GRATIS");
        assertThat(resposta.limites()).isEqualTo(new PlanoResponse.Limites(3, 2));
        assertThat(resposta.recursos()).isEmpty();
        assertThat(resposta.assinatura()).isNull();
    }

    @Test
    void proTemLimitesDoProTodosOsRecursosEAAssinaturaResumida() {
        var assinatura = Assinatura.nova(UUID.randomUUID(), "sub_123", CicloAssinatura.ANUAL,
                StatusAssinatura.ACTIVE, FIM, true, AGORA);

        var resposta = PlanoResponse.de(PlanoDoUsuario.pro(assinatura));

        assertThat(resposta.plano()).isEqualTo("PRO");
        assertThat(resposta.limites()).isEqualTo(new PlanoResponse.Limites(10, 10));
        assertThat(resposta.recursos()).containsExactly("JULIA", "TEMAS_POSSIVEIS", "REPERTORIO_COMPLETO");
        assertThat(resposta.assinatura())
                .isEqualTo(new PlanoResponse.AssinaturaResumo(CicloAssinatura.ANUAL, FIM, true));
    }

    @Test
    void naoExpoeIdsDoStripeNemStatusCru() {
        assertThat(PlanoResponse.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("plano", "limites", "recursos", "assinatura");
        assertThat(PlanoResponse.AssinaturaResumo.class.getRecordComponents())
                .extracting(c -> c.getName())
                .containsExactly("ciclo", "periodoAtualFim", "cancelarNoFimDoPeriodo");
    }
}
